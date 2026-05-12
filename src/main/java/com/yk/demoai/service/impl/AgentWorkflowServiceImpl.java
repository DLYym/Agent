package com.yk.demoai.service.impl;

import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.exception.AgentWorkflowException;
import com.yk.demoai.model.AgentPendingAction;
import com.yk.demoai.model.AgentWorkflowResult;
import com.yk.demoai.model.AgentWorkflowStep;
import com.yk.demoai.model.ColumnSchema;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaRetrievalContext;
import com.yk.demoai.model.SchemaSnapshot;
import com.yk.demoai.model.TableSchema;
import com.yk.demoai.model.WorkflowTokenUsage;
import com.yk.demoai.service.AgentWorkflowService;
import com.yk.demoai.service.AgentWorkflowStreamListener;
import com.yk.demoai.service.DatabaseSchemaExtractor;
import com.yk.demoai.service.ReadOnlySqlExecutor;
import com.yk.demoai.service.SchemaHybridRetriever;
import com.yk.demoai.service.SqlGenerator;
import com.yk.demoai.service.SqlRepairGenerator;
import com.yk.demoai.service.StreamingSqlGenerator;
import com.yk.demoai.service.StreamingSqlRepairGenerator;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 以 ReAct 方式编排 Text-to-SQL 任务，并在关键节点显式交还给用户做确认。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentWorkflowServiceImpl implements AgentWorkflowService {

    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([`\"\\[]?[\\w.]+[`\"\\]]?)"
    );
    private static final Pattern CTE_NAME_PATTERN = Pattern.compile(
            "(?i)(?:with|,)\\s*([a-zA-Z_][\\w]*)\\s+as\\s*\\("
    );

    private final SqlGenerator sqlGenerator;
    private final SqlRepairGenerator sqlRepairGenerator;
    private final StreamingSqlGenerator streamingSqlGenerator;
    private final StreamingSqlRepairGenerator streamingSqlRepairGenerator;
    private final SchemaHybridRetriever schemaHybridRetriever;
    private final ReadOnlySqlExecutor readOnlySqlExecutor;
    private final DatabaseSchemaExtractor databaseSchemaExtractor;
    private final AppProperties appProperties;

    @Override
    public AgentWorkflowResult previewWorkflow(String question,
                                               List<DatasourceDescriptor> datasources,
                                               String targetDatasourceId) {
        PreparedWorkflowContext preparedContext = prepareWorkflowContext(
                question,
                datasources,
                targetDatasourceId,
                AgentWorkflowStreamListener.NO_OP
        );
        preparedContext.steps().add(step(
                "await_user_generation_approval",
                "等待生成授权",
                "completed",
                "当前 reasoning 阶段已完成，等待用户确认后进入 SQL 生成。",
                detailMap(
                        "datasourceId", preparedContext.schemaContext().targetDatasource().id(),
                        "matchedTables", preparedContext.matchedTables()
                )
        ));
        return new AgentWorkflowResult(
                preparedContext.schemaContext().targetDatasource(),
                "",
                List.of(),
                preparedContext.matchedTables(),
                WorkflowTokenUsage.empty(),
                List.copyOf(preparedContext.steps()),
                0,
                "awaiting_generation_approval",
                pendingAction(
                        "generate_sql",
                        "确认检索上下文",
                        "当前已锁定候选表和 Schema 上下文，确认无误后再生成 SQL。",
                        "确认并生成 SQL"
                )
        );
    }

    @Override
    public AgentWorkflowResult runDraftWorkflow(String question,
                                                List<DatasourceDescriptor> datasources,
                                                String targetDatasourceId,
                                                AgentWorkflowStreamListener streamListener) {
        return runWorkflow(question, datasources, targetDatasourceId, false, streamListener);
    }

    @Override
    public AgentWorkflowResult runGenerateWorkflow(String question,
                                                   List<DatasourceDescriptor> datasources,
                                                   String targetDatasourceId) {
        return runWorkflow(question, datasources, targetDatasourceId, true, AgentWorkflowStreamListener.NO_OP);
    }

    /**
     * 统一封装生成阶段。可以根据场景决定生成后是交还给用户，还是直接继续执行。
     */
    private AgentWorkflowResult runWorkflow(String question,
                                            List<DatasourceDescriptor> datasources,
                                            String targetDatasourceId,
                                            boolean executeAfterGeneration,
                                            AgentWorkflowStreamListener streamListener) {
        PreparedWorkflowContext preparedContext = prepareWorkflowContext(question, datasources, targetDatasourceId, streamListener);
        List<AgentWorkflowStep> steps = preparedContext.steps();
        SchemaRetrievalContext schemaContext = preparedContext.schemaContext();
        SchemaSnapshot schemaSnapshot = preparedContext.schemaSnapshot();
        List<String> matchedTables = preparedContext.matchedTables();
        TokenUsageAccumulator tokenUsageAccumulator = new TokenUsageAccumulator();

        appendStep(steps, streamListener, step(
                "prepare_generation",
                "生成准备",
                "completed",
                "已整理上下文，开始请求模型生成 SQL 草稿。",
                detailMap(
                        "datasourceId", schemaContext.targetDatasource().id(),
                        "matchedTables", matchedTables
                )
        ));

        int maxAttempts = Math.max(1, appProperties.getWorkflow().getMaxRepairAttempts() + 1);
        String candidateSql = generateDraftSql(question, schemaContext, 1, streamListener, tokenUsageAccumulator);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            appendStep(steps, streamListener, step(
                    "draft_sql",
                    "SQL 草拟",
                    "completed",
                    "已生成第 " + attempt + " 版 SQL 草稿。",
                    detailMap(
                            "attempt", attempt,
                            "sql", candidateSql
                    )
            ));

            ValidationResult validationResult = validateSql(candidateSql, schemaSnapshot);
            if (!validationResult.valid()) {
                appendStep(steps, streamListener, step(
                        "validate_sql",
                        "SQL 校验",
                        "failed",
                        validationResult.message(),
                        detailMap(
                                "attempt", attempt,
                                "referencedTables", validationResult.referencedTables()
                        )
                ));
                candidateSql = repairOrThrow(
                        question,
                        schemaContext,
                        candidateSql,
                        validationResult.message(),
                        attempt,
                        maxAttempts,
                        steps,
                        streamListener,
                        tokenUsageAccumulator,
                        null
                );
                continue;
            }

            appendStep(steps, streamListener, step(
                    "validate_sql",
                    "SQL 校验",
                    "completed",
                    "SQL 通过只读和表引用校验。",
                    detailMap(
                            "attempt", attempt,
                            "referencedTables", validationResult.referencedTables()
                    )
            ));

            boolean shouldExecute = executeAfterGeneration && appProperties.getWorkflow().isAutoExecuteGeneratedSql();
            if (!shouldExecute) {
                appendStep(steps, streamListener, step(
                        "await_user_execution_approval",
                        "等待执行授权",
                        "completed",
                        "SQL 已生成并通过校验，等待用户确认后执行。",
                        detailMap(
                                "attempt", attempt,
                                "sql", candidateSql
                        )
                ));
                return new AgentWorkflowResult(
                        schemaContext.targetDatasource(),
                        candidateSql,
                        List.of(),
                        matchedTables,
                        tokenUsageAccumulator.snapshot(),
                        List.copyOf(steps),
                        attempt,
                        "ready_for_execution",
                        pendingAction(
                                "execute_sql",
                                "确认 SQL 并执行",
                                "SQL 已准备好，请先审阅再决定是否执行。",
                                "前往执行 SQL"
                        )
                );
            }

            try {
                List<Map<String, Object>> data = readOnlySqlExecutor.execute(schemaContext.targetDatasource(), candidateSql);
                appendStep(steps, streamListener, step(
                        "execute_sql",
                        "SQL 执行",
                        "completed",
                        "SQL 执行成功，返回 " + data.size() + " 行结果。",
                        detailMap(
                                "attempt", attempt,
                                "rowCount", data.size()
                        )
                ));
                return new AgentWorkflowResult(
                        schemaContext.targetDatasource(),
                        candidateSql,
                        data,
                        matchedTables,
                        tokenUsageAccumulator.snapshot(),
                        List.copyOf(steps),
                        attempt,
                        "completed",
                        null
                );
            } catch (Exception ex) {
                appendStep(steps, streamListener, step(
                        "execute_sql",
                        "SQL 执行",
                        "failed",
                        ex.getMessage(),
                        detailMap("attempt", attempt)
                ));
                candidateSql = repairOrThrow(
                        question,
                        schemaContext,
                        candidateSql,
                        ex.getMessage(),
                        attempt,
                        maxAttempts,
                        steps,
                        streamListener,
                        tokenUsageAccumulator,
                        ex
                );
            }
        }

        throw new AgentWorkflowException("Agent 工作流已结束，但未生成可执行 SQL。", steps, candidateSql);
    }

    /**
     * 构建 reasoning 阶段的上下文，供 preview 和 generate 两个阶段复用。
     */
    private PreparedWorkflowContext prepareWorkflowContext(String question,
                                                           List<DatasourceDescriptor> datasources,
                                                           String targetDatasourceId,
                                                           AgentWorkflowStreamListener streamListener) {
        List<AgentWorkflowStep> steps = new ArrayList<>();
        appendStep(steps, streamListener, step(
                "plan",
                "任务规划",
                "completed",
                buildPlanningSummary(question, datasources, targetDatasourceId),
                detailMap(
                        "question", question,
                        "datasourceCount", datasources.size(),
                        "targetDatasourceHint", StringUtils.hasText(targetDatasourceId) ? targetDatasourceId : "auto-select"
                )
        ));

        SchemaRetrievalContext schemaContext = schemaHybridRetriever.retrieve(question, datasources, targetDatasourceId);
        List<String> matchedTables = schemaContext.hits().stream()
                .map(hit -> hit.document().tableName())
                .distinct()
                .toList();
        appendStep(steps, streamListener, step(
                "retrieve_schema",
                "Schema 检索",
                "completed",
                "已选择数据源 " + schemaContext.targetDatasource().displayName() + "，命中 " + matchedTables.size() + " 张候选表。",
                detailMap(
                        "datasourceId", schemaContext.targetDatasource().id(),
                        "databaseType", schemaContext.databaseType(),
                        "matchedTables", matchedTables
                )
        ));

        SchemaSnapshot schemaSnapshot = databaseSchemaExtractor.extract(schemaContext.targetDatasource());
        List<String> tableSummaries = buildTableSummaries(schemaSnapshot, matchedTables);
        appendStep(steps, streamListener, step(
                "inspect_schema",
                "Schema 观察",
                "completed",
                tableSummaries.isEmpty() ? "未抽取到显式候选表摘要，将基于全库 Schema 继续推理。" : "已整理候选表结构摘要，便于用户确认当前 reasoning 依据。",
                detailMap(
                        "tableSummaries", tableSummaries,
                        "tableCount", schemaSnapshot.tables().size()
                )
        ));
        return new PreparedWorkflowContext(schemaContext, schemaSnapshot, matchedTables, steps);
    }

    private String repairOrThrow(String question,
                                 SchemaRetrievalContext schemaContext,
                                 String candidateSql,
                                 String feedback,
                                 int attempt,
                                 int maxAttempts,
                                 List<AgentWorkflowStep> steps,
                                 AgentWorkflowStreamListener streamListener,
                                 TokenUsageAccumulator tokenUsageAccumulator,
                                 Exception cause) {
        if (attempt >= maxAttempts) {
            throw new AgentWorkflowException("SQL 多轮修复后仍失败: " + feedback, steps, candidateSql, cause);
        }

        String repairedSql = repairSql(question, schemaContext, candidateSql, feedback, attempt + 1, streamListener, tokenUsageAccumulator);
        appendStep(steps, streamListener, step(
                "repair_sql",
                "SQL 修复",
                "completed",
                "已根据失败反馈生成第 " + (attempt + 1) + " 版 SQL。",
                detailMap(
                        "attempt", attempt + 1,
                        "reason", feedback,
                        "sql", repairedSql
                )
        ));
        return repairedSql;
    }

    /**
     * 根据调用场景决定使用同步模型还是流式模型来生成 SQL。
     */
    private String generateDraftSql(String question,
                                    SchemaRetrievalContext schemaContext,
                                    int attempt,
                                    AgentWorkflowStreamListener streamListener,
                                    TokenUsageAccumulator tokenUsageAccumulator) {
        String logicalRelations = schemaContext.logicalRelationsContext();
        String foreignKeyReplacementRules = schemaContext.foreignKeyReplacementRules();
        String dictMappingReplacementRules = schemaContext.dictMappingReplacementRules();
        if (!streamListener.streamLlmTokens()) {
            return sanitizeSql(sqlGenerator.generate(
                    schemaContext.schemaContext(),
                    schemaContext.databaseType(),
                    logicalRelations,
                    foreignKeyReplacementRules,
                    dictMappingReplacementRules,
                    question
            ));
        }
        return sanitizeSql(streamSqlText(
                "draft_sql",
                "SQL 草拟",
                attempt,
                streamListener,
                tokenUsageAccumulator,
                () -> streamingSqlGenerator.generate(
                        schemaContext.schemaContext(),
                        schemaContext.databaseType(),
                        logicalRelations,
                        foreignKeyReplacementRules,
                        dictMappingReplacementRules,
                        question
                )
        ));
    }

    /**
     * SQL 修复阶段同样支持 token 级流式输出，便于用户看到重试过程。
     */
    private String repairSql(String question,
                             SchemaRetrievalContext schemaContext,
                             String candidateSql,
                             String feedback,
                             int attempt,
                             AgentWorkflowStreamListener streamListener,
                             TokenUsageAccumulator tokenUsageAccumulator) {
        String logicalRelations = schemaContext.logicalRelationsContext();
        String foreignKeyReplacementRules = schemaContext.foreignKeyReplacementRules();
        String dictMappingReplacementRules = schemaContext.dictMappingReplacementRules();
        if (!streamListener.streamLlmTokens()) {
            return sanitizeSql(sqlRepairGenerator.repair(
                    schemaContext.schemaContext(),
                    schemaContext.databaseType(),
                    logicalRelations,
                    foreignKeyReplacementRules,
                    dictMappingReplacementRules,
                    question,
                    candidateSql,
                    feedback
            ));
        }
        return sanitizeSql(streamSqlText(
                "repair_sql",
                "SQL 修复",
                attempt,
                streamListener,
                tokenUsageAccumulator,
                () -> streamingSqlRepairGenerator.repair(
                        schemaContext.schemaContext(),
                        schemaContext.databaseType(),
                        logicalRelations,
                        foreignKeyReplacementRules,
                        dictMappingReplacementRules,
                        question,
                        candidateSql,
                        feedback
                )
        ));
    }

    /**
     * 把流式模型回调转换成阻塞式结果，方便继续走后续校验和执行逻辑。
     */
    private String streamSqlText(String phaseCode,
                                 String phaseTitle,
                                 int attempt,
                                 AgentWorkflowStreamListener streamListener,
                                 TokenUsageAccumulator tokenUsageAccumulator,
                                 Supplier<TokenStream> tokenStreamSupplier) {
        StringBuilder responseBuilder = new StringBuilder();
        AtomicReference<String> completedTextRef = new AtomicReference<>("");
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        streamListener.onLlmStart(phaseCode, phaseTitle, attempt);
        tokenStreamSupplier.get()
                .onPartialResponse(token -> {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    responseBuilder.append(token);
                    streamListener.onLlmToken(phaseCode, phaseTitle, attempt, token);
                })
                .onCompleteResponse(chatResponse -> {
                    String completedText = responseBuilder.toString();
                    if (!StringUtils.hasText(completedText) && chatResponse.aiMessage() != null) {
                        completedText = chatResponse.aiMessage().text();
                    }
                    WorkflowTokenUsage tokenUsage = resolveWorkflowTokenUsage(chatResponse);
                    tokenUsageAccumulator.add(tokenUsage);
                    completedTextRef.set(completedText);
                    streamListener.onLlmComplete(phaseCode, phaseTitle, attempt, completedText);
                    streamListener.onLlmUsage(phaseCode, phaseTitle, attempt, tokenUsage);
                    latch.countDown();
                })
                .onError(error -> {
                    errorRef.set(error);
                    latch.countDown();
                })
                .start();

        try {
            boolean completed = latch.await(
                    appProperties.getWorkflow().getLlmStreamTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!completed) {
                throw new IllegalStateException("LLM 流式生成超时，请稍后重试。");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 流式生成被中断。", ex);
        }

        if (errorRef.get() != null) {
            throw new IllegalStateException("LLM 流式生成失败: " + errorRef.get().getMessage(), errorRef.get());
        }
        return StringUtils.hasText(completedTextRef.get()) ? completedTextRef.get() : responseBuilder.toString();
    }

    private ValidationResult validateSql(String sql, SchemaSnapshot schemaSnapshot) {
        if (!readOnlySqlExecutor.isReadOnly(sql)) {
            return new ValidationResult(false, "SQL 校验失败：仅允许执行只读语句。", List.of());
        }

        Set<String> availableTables = schemaSnapshot.tables().stream()
                .map(table -> normalizeIdentifier(table.tableName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> cteNames = extractCteNames(sql);
        List<String> referencedTables = extractReferencedTables(sql).stream()
                .filter(table -> !cteNames.contains(table))
                .toList();

        List<String> unknownTables = referencedTables.stream()
                .filter(table -> !availableTables.contains(table))
                .distinct()
                .toList();
        if (!unknownTables.isEmpty()) {
            return new ValidationResult(
                    false,
                    "SQL 校验失败：引用了当前数据源中不存在的表 " + unknownTables,
                    referencedTables
            );
        }

        return new ValidationResult(true, "ok", referencedTables);
    }

    private List<String> extractReferencedTables(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
        while (matcher.find()) {
            tables.add(normalizeIdentifier(matcher.group(1)));
        }
        return tables;
    }

    /**
     * 识别 WITH 子句中定义的临时结果集，避免把 CTE 名称误判成真实表。
     */
    private Set<String> extractCteNames(String sql) {
        Set<String> cteNames = new LinkedHashSet<>();
        Matcher matcher = CTE_NAME_PATTERN.matcher(sql);
        while (matcher.find()) {
            cteNames.add(normalizeIdentifier(matcher.group(1)));
        }
        return cteNames;
    }

    private String normalizeIdentifier(String rawIdentifier) {
        String normalized = rawIdentifier == null ? "" : rawIdentifier
                .replace("`", "")
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "")
                .trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex + 1 < normalized.length()) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized.toLowerCase();
    }

    private String buildPlanningSummary(String question,
                                        List<DatasourceDescriptor> datasources,
                                        String targetDatasourceId) {
        if (datasources.size() == 1) {
            return "将围绕问题“" + question + "”在单个数据源上完成检索、生成和执行。";
        }
        if (StringUtils.hasText(targetDatasourceId)) {
            return "用户已指定目标数据源 " + targetDatasourceId + "，工作流会优先在该数据源上执行。";
        }
        return "检测到多数据源输入，工作流会先做 Schema 混合召回，再决定最合适的目标库。";
    }

    /**
     * 用紧凑摘要告诉前端“当前正在依据哪些表推理”，避免用户只看到最后的 SQL。
     */
    private List<String> buildTableSummaries(SchemaSnapshot schemaSnapshot, List<String> matchedTables) {
        Set<String> matchedTableSet = matchedTables.stream()
                .map(this::normalizeIdentifier)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<TableSchema> candidateTables = schemaSnapshot.tables().stream()
                .filter(table -> matchedTableSet.isEmpty() || matchedTableSet.contains(normalizeIdentifier(table.tableName())))
                .limit(3)
                .toList();

        return candidateTables.stream()
                .map(table -> {
                    String comment = StringUtils.hasText(table.tableComment()) ? "（" + table.tableComment() + "）" : "";
                    String columns = table.columns().stream()
                            .limit(6)
                            .map(ColumnSchema::name)
                            .collect(Collectors.joining(", "));
                    return table.tableName() + comment + " -> " + columns;
                })
                .toList();
    }

    private String sanitizeSql(String rawSql) {
        String cleanSql = rawSql == null ? "" : rawSql
                .replace("```sql", "")
                .replace("```", "")
                .replace("\n", " ")
                .trim();
        if (cleanSql.endsWith(";")) {
            cleanSql = cleanSql.substring(0, cleanSql.length() - 1);
        }
        log.info("Agent SQL 候选结果: {}", cleanSql);
        return cleanSql;
    }

    /**
     * LangChain4j 会把 usage 挂在 ChatResponse 或 metadata 上，这里统一抹平成前端可直接展示的结构。
     */
    private WorkflowTokenUsage resolveWorkflowTokenUsage(dev.langchain4j.model.chat.response.ChatResponse chatResponse) {
        if (chatResponse == null) {
            return WorkflowTokenUsage.empty();
        }
        TokenUsage directUsage = chatResponse.tokenUsage();
        if (directUsage != null) {
            return WorkflowTokenUsage.from(directUsage);
        }
        if (chatResponse.metadata() != null) {
            return WorkflowTokenUsage.from(chatResponse.metadata().tokenUsage());
        }
        return WorkflowTokenUsage.empty();
    }

    private AgentWorkflowStep step(String code,
                                   String title,
                                   String status,
                                   String summary,
                                   Map<String, Object> details) {
        return new AgentWorkflowStep(code, title, status, summary, details);
    }

    /**
     * 新步骤一旦产生就立即通知监听器，供前端做流式思考过程展示。
     */
    private void appendStep(List<AgentWorkflowStep> steps,
                            AgentWorkflowStreamListener streamListener,
                            AgentWorkflowStep step) {
        steps.add(step);
        streamListener.onStep(step);
    }

    /**
     * 使用 LinkedHashMap 保证步骤详情的序列化顺序稳定，便于前端展示。
     */
    private Map<String, Object> detailMap(Object... values) {
        LinkedHashMap<String, Object> detailMap = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            Object key = values[index];
            Object value = values[index + 1];
            if (key != null && value != null) {
                detailMap.put(String.valueOf(key), value);
            }
        }
        return detailMap;
    }

    private AgentPendingAction pendingAction(String code,
                                             String title,
                                             String description,
                                             String buttonLabel) {
        return new AgentPendingAction(code, title, description, buttonLabel);
    }

    private record ValidationResult(boolean valid, String message, List<String> referencedTables) {
    }

    private record PreparedWorkflowContext(SchemaRetrievalContext schemaContext,
                                          SchemaSnapshot schemaSnapshot,
                                          List<String> matchedTables,
                                          List<AgentWorkflowStep> steps) {
    }

    /**
     * 同一次工作流可能经过草拟和多轮修复，这里累计总 token。
     */
    private static final class TokenUsageAccumulator {
        private WorkflowTokenUsage total = WorkflowTokenUsage.empty();

        private void add(WorkflowTokenUsage tokenUsage) {
            total = total.add(tokenUsage);
        }

        private WorkflowTokenUsage snapshot() {
            return total;
        }
    }
}

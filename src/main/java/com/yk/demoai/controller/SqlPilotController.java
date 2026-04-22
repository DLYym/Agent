package com.yk.demoai.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yk.demoai.dto.SqlRequest;
import com.yk.demoai.exception.AgentWorkflowException;
import com.yk.demoai.model.AgentPendingAction;
import com.yk.demoai.model.AgentWorkflowResult;
import com.yk.demoai.model.WorkflowTokenUsage;
import com.yk.demoai.service.ISqlService;
import com.yk.demoai.service.AgentWorkflowStreamListener;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/sql")
public class SqlPilotController {

    @Resource
    private ISqlService sqlService;

    @Resource
    private ObjectMapper objectMapper;

    @PostMapping(value = "/analyze-stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> analyzeSqlStream(@RequestBody SqlRequest request) {
        StreamingResponseBody responseBody = outputStream -> {
            try {
                streamWorkflow(request, outputStream);
            } catch (Exception ex) {
                throw new IllegalStateException("流式分析失败: " + ex.getMessage(), ex);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .header("Connection", "keep-alive")
                .body(responseBody);
    }

    @PostMapping("/preview")
    public Map<String, Object> previewSqlWorkflow(@RequestBody SqlRequest request) {
        return sqlService.previewSqlWorkflow(request);
    }

    @PostMapping("/generate")
    public Map<String, Object> generateSql(@RequestBody SqlRequest request) {
        return sqlService.generateSql(request);
    }

    @PostMapping("/execute")
    public Map<String, Object> executeSql(@RequestBody SqlRequest request) {
        return sqlService.executeSql(request);
    }

    @PostMapping("/test-connection")
    public Map<String, String> testConnection(@RequestBody SqlRequest request) {
        return sqlService.testConnection(request);
    }

    @PostMapping("/databases")
    public Map<String, Object> listDatabases(@RequestBody SqlRequest request) {
        return sqlService.listDatabases(request);
    }

    /**
     * 使用 NDJSON 连续输出工作流步骤，让前端在分析过程中实时展示 reasoning 轨迹。
     */
    private void streamWorkflow(SqlRequest request, OutputStream outputStream) throws Exception {
        AtomicInteger stepIndex = new AtomicInteger(0);
        java.util.function.Consumer<Map<String, Object>> emit = payload -> writeChunk(outputStream, payload);

        emit.accept(Map.of(
                "type", "status",
                "workflowStatus", "analyzing",
                "message", "开始分析问题，正在整理上下文。"
        ));

        try {
            AgentWorkflowResult workflowResult = sqlService.analyzeSqlWorkflow(request, new AgentWorkflowStreamListener() {
                @Override
                public boolean streamLlmTokens() {
                    return true;
                }

                @Override
                public void onStep(com.yk.demoai.model.AgentWorkflowStep step) {
                    emit.accept(Map.of(
                            "type", "step",
                            "index", stepIndex.incrementAndGet(),
                            "step", step,
                            "message", step.summary(),
                            "workflowStatus", "analyzing"
                    ));
                }

                @Override
                public void onLlmStart(String phaseCode, String phaseTitle, int attempt) {
                    emit.accept(Map.of(
                            "type", "llm_start",
                            "phaseCode", phaseCode,
                            "phaseTitle", phaseTitle,
                            "attempt", attempt,
                            "workflowStatus", "analyzing",
                            "message", phaseTitle + " 已开始，正在流式输出模型结果。"
                    ));
                }

                @Override
                public void onLlmToken(String phaseCode, String phaseTitle, int attempt, String token) {
                    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "llm_token");
                    payload.put("phaseCode", phaseCode);
                    payload.put("phaseTitle", phaseTitle);
                    payload.put("attempt", attempt);
                    payload.put("token", token);
                    payload.put("workflowStatus", "analyzing");
                    emit.accept(payload);
                }

                @Override
                public void onLlmComplete(String phaseCode, String phaseTitle, int attempt, String fullText) {
                    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "llm_complete");
                    payload.put("phaseCode", phaseCode);
                    payload.put("phaseTitle", phaseTitle);
                    payload.put("attempt", attempt);
                    payload.put("text", fullText);
                    payload.put("workflowStatus", "analyzing");
                    emit.accept(payload);
                }

                @Override
                public void onLlmUsage(String phaseCode, String phaseTitle, int attempt, WorkflowTokenUsage tokenUsage) {
                    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "llm_usage");
                    payload.put("phaseCode", phaseCode);
                    payload.put("phaseTitle", phaseTitle);
                    payload.put("attempt", attempt);
                    payload.put("tokenUsage", tokenUsage);
                    payload.put("workflowStatus", "analyzing");
                    emit.accept(payload);
                }
            });
            emit.accept(buildResultEvent(workflowResult));
        } catch (AgentWorkflowException ex) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "error");
            payload.put("workflowStatus", "failed");
            payload.put("error", ex.getMessage());
            payload.put("steps", ex.getSteps());
            if (StringUtils.hasText(ex.getLastSql())) {
                payload.put("sql", ex.getLastSql());
            }
            emit.accept(payload);
        } catch (Exception ex) {
            emit.accept(Map.of(
                    "type", "error",
                    "workflowStatus", "failed",
                    "error", ex.getMessage() == null ? "分析失败" : ex.getMessage()
            ));
        }
    }

    private Map<String, Object> buildResultEvent(AgentWorkflowResult workflowResult) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "result");
        payload.put("workflowStatus", workflowResult.workflowStatus());
        payload.put("sql", workflowResult.sql());
        payload.put("data", workflowResult.data());
        payload.put("count", workflowResult.data().size());
        payload.put("datasourceId", workflowResult.targetDatasource().id());
        payload.put("datasourceName", workflowResult.targetDatasource().displayName());
        payload.put("matchedTables", workflowResult.matchedTables());
        payload.put("tokenUsage", workflowResult.tokenUsage());
        payload.put("steps", workflowResult.steps());
        payload.put("attempts", workflowResult.attempts());
        AgentPendingAction pendingAction = workflowResult.pendingAction();
        if (pendingAction != null) {
            payload.put("pendingAction", pendingAction);
        }
        payload.put("message", "分析完成，SQL 已准备就绪。");
        return payload;
    }

    private void writeChunk(OutputStream outputStream, Map<String, Object> payload) {
        try {
            outputStream.write(objectMapper.writeValueAsBytes(payload));
            outputStream.write('\n');
            outputStream.flush();
        } catch (Exception ex) {
            throw new IllegalStateException("流式输出工作流失败: " + ex.getMessage(), ex);
        }
    }
}

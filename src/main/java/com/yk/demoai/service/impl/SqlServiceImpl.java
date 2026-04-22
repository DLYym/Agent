package com.yk.demoai.service.impl;

import com.yk.demoai.dto.DatasourceRequest;
import com.yk.demoai.dto.SqlRequest;
import com.yk.demoai.model.AgentPendingAction;
import com.yk.demoai.model.AgentWorkflowResult;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.service.AgentWorkflowService;
import com.yk.demoai.service.AgentWorkflowStreamListener;
import com.yk.demoai.service.DatabaseSchemaExtractor;
import com.yk.demoai.service.DatabaseTypeAdapter;
import com.yk.demoai.service.DynamicDataSourceManager;
import com.yk.demoai.service.ISqlService;
import com.yk.demoai.service.ReadOnlySqlExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 组装 Schema 检索、SQL 生成和只读执行这条主流程。
 */
@Service
@RequiredArgsConstructor
public class SqlServiceImpl implements ISqlService {

    private final AgentWorkflowService agentWorkflowService;
    private final ReadOnlySqlExecutor readOnlySqlExecutor;
    private final DatabaseSchemaExtractor databaseSchemaExtractor;
    private final DynamicDataSourceManager dataSourceManager;
    private final DatabaseTypeAdapter databaseTypeAdapter;

    @Override
    public Map<String, Object> previewSqlWorkflow(SqlRequest request) {
        if (!StringUtils.hasText(request.getQuestion())) {
            throw new IllegalArgumentException("问题不能为空");
        }

        List<DatasourceDescriptor> datasources = resolveDatasources(request);
        AgentWorkflowResult workflowResult = agentWorkflowService.previewWorkflow(
                request.getQuestion(),
                datasources,
                request.getTargetDatasourceId()
        );
        return buildWorkflowResponse(workflowResult);
    }

    @Override
    public Map<String, Object> generateSql(SqlRequest request) {
        if (!StringUtils.hasText(request.getQuestion())) {
            throw new IllegalArgumentException("问题不能为空");
        }

        List<DatasourceDescriptor> datasources = resolveDatasources(request);
        AgentWorkflowResult workflowResult = agentWorkflowService.runDraftWorkflow(
                request.getQuestion(),
                datasources,
                request.getTargetDatasourceId()
        );

        return buildWorkflowResponse(workflowResult);
    }

    @Override
    public AgentWorkflowResult analyzeSqlWorkflow(SqlRequest request, AgentWorkflowStreamListener streamListener) {
        if (!StringUtils.hasText(request.getQuestion())) {
            throw new IllegalArgumentException("问题不能为空");
        }

        List<DatasourceDescriptor> datasources = resolveDatasources(request);
        return agentWorkflowService.runDraftWorkflow(
                request.getQuestion(),
                datasources,
                request.getTargetDatasourceId(),
                streamListener
        );
    }

    @Override
    public Map<String, Object> executeSql(SqlRequest request) {
        if (!StringUtils.hasText(request.getSql())) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        DatasourceDescriptor datasource = resolveExecutionDatasource(request);
        List<Map<String, Object>> resultList = readOnlySqlExecutor.execute(datasource, sanitizeSql(request.getSql()));
        List<String> columns = resultList.isEmpty()
                ? List.of()
                : new java.util.ArrayList<>(resultList.getFirst().keySet());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", resultList);
        response.put("count", resultList.size());
        response.put("columns", columns);
        response.put("datasourceId", datasource.id());
        response.put("datasourceName", datasource.displayName());
        response.put("message", resultList.isEmpty()
                ? "SQL 执行成功，但当前条件下没有返回数据。"
                : "SQL 执行成功，返回 " + resultList.size() + " 条记录。");
        return response;
    }

    @Override
    public Map<String, String> testConnection(SqlRequest request) {
        List<DatasourceDescriptor> datasources = resolveDatasources(request);
        for (DatasourceDescriptor datasource : datasources) {
            databaseSchemaExtractor.extract(datasource);
        }
        return Map.of(
                "status", "success",
                "message", "连接成功，已校验 " + datasources.size() + " 个数据源。"
        );
    }

    @Override
    public Map<String, Object> listDatabases(SqlRequest request) {
        DatasourceDescriptor datasource = resolveConnectionDatasource(request);
        TreeSet<String> databaseNames = new TreeSet<>();
        
        try (Connection connection = resolveConnectionDatasourceConnection(datasource)) {
            // 首先尝试使用JDBC元数据获取catalogs
            try (ResultSet resultSet = connection.getMetaData().getCatalogs()) {
                while (resultSet.next()) {
                    String catalog = resultSet.getString(1);
                    if (StringUtils.hasText(catalog) && !isSystemDatabase(catalog)) {
                        databaseNames.add(catalog);
                    }
                }
            }

            // 如果元数据方式没有获取到数据，则使用数据库特定的查询方式
            if (databaseNames.isEmpty()) {
                // 识别数据库类型
                DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
                
                String databaseListQuery = databaseTypeAdapter.generateDatabaseListQuery(dbType);
                if (databaseListQuery != null) {
                    try (ResultSet resultSet = connection.createStatement().executeQuery(databaseListQuery)) {
                        while (resultSet.next()) {
                            String databaseName = resultSet.getString(1);
                            if (StringUtils.hasText(databaseName) && !isSystemDatabase(databaseName)) {
                                databaseNames.add(databaseName);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("加载数据库列表失败: " + ex.getMessage(), ex);
        }

        return Map.of(
                "status", "success",
                "message", databaseNames.isEmpty() ? "连接成功，但未发现可选数据库。" : "连接成功，请选择数据库。",
                "databases", List.copyOf(databaseNames)
        );
    }

    private List<DatasourceDescriptor> resolveDatasources(SqlRequest request) {
        if (request.getDatasources() != null && !request.getDatasources().isEmpty()) {
            return request.getDatasources().stream()
                    .map(this::toDescriptor)
                    .toList();
        }
        if (!StringUtils.hasText(request.getDbUrl())) {
            throw new IllegalArgumentException("缺少数据库连接信息");
        }
        return List.of(new DatasourceDescriptor(
                request.getTargetDatasourceId(),
                "default",
                request.getDbUrl(),
                request.getUsername(),
                request.getPassword(),
                request.getDriverClassName(),
                request.getCatalog(),
                request.getSchemaName(),
                request.getTableNames() == null ? List.of() : List.of(request.getTableNames())
        ));
    }

    private DatasourceDescriptor resolveExecutionDatasource(SqlRequest request) {
        List<DatasourceDescriptor> datasources = resolveDatasources(request);
        if (datasources.size() == 1) {
            return datasources.getFirst();
        }
        if (StringUtils.hasText(request.getTargetDatasourceId())) {
            return datasources.stream()
                    .filter(datasource -> datasource.id().equals(request.getTargetDatasourceId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未找到目标数据源: " + request.getTargetDatasourceId()));
        }
        throw new IllegalArgumentException("多数据源执行 SQL 时，请指定 targetDatasourceId");
    }

    private DatasourceDescriptor resolveConnectionDatasource(SqlRequest request) {
        if (!StringUtils.hasText(request.getDbUrl())) {
            throw new IllegalArgumentException("缺少数据库连接信息");
        }
        return new DatasourceDescriptor(
                "connection-preview",
                "connection-preview",
                request.getDbUrl(),
                request.getUsername(),
                request.getPassword(),
                request.getDriverClassName(),
                request.getCatalog(),
                request.getSchemaName(),
                List.of()
        );
    }

    private Connection resolveConnectionDatasourceConnection(DatasourceDescriptor datasource) throws Exception {
        // 这里复用动态数据源缓存，避免仅为了拉取库列表重复建连接池。
        return dataSourceManager.getDataSource(datasource).getConnection();
    }

    private DatasourceDescriptor toDescriptor(DatasourceRequest request) {
        if (!StringUtils.hasText(request.getDbUrl())) {
            throw new IllegalArgumentException("datasources[].dbUrl 不能为空");
        }
        return new DatasourceDescriptor(
                request.getId(),
                request.getName(),
                request.getDbUrl(),
                request.getUsername(),
                request.getPassword(),
                request.getDriverClassName(),
                request.getCatalog(),
                request.getSchemaName(),
                request.getTableNames() == null ? List.of() : List.of(request.getTableNames())
        );
    }

    private boolean isSystemDatabase(String databaseName) {
        String normalized = databaseName.toLowerCase();
        return normalized.equals("information_schema")
                || normalized.equals("mysql")
                || normalized.equals("performance_schema")
                || normalized.equals("sys");
    }

    /**
     * 统一组装 workflow 响应，方便 preview/generate 两个阶段复用。
     */
    private Map<String, Object> buildWorkflowResponse(AgentWorkflowResult workflowResult) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sql", workflowResult.sql());
        response.put("data", workflowResult.data());
        response.put("count", workflowResult.data().size());
        response.put("datasourceId", workflowResult.targetDatasource().id());
        response.put("datasourceName", workflowResult.targetDatasource().displayName());
        response.put("matchedTables", workflowResult.matchedTables());
        response.put("tokenUsage", workflowResult.tokenUsage());
        response.put("steps", workflowResult.steps());
        response.put("attempts", workflowResult.attempts());
        response.put("workflowStatus", workflowResult.workflowStatus());
        AgentPendingAction pendingAction = workflowResult.pendingAction();
        if (pendingAction != null) {
            response.put("pendingAction", pendingAction);
        }
        return response;
    }

    /**
     * 兼容用户手工编辑 SQL 时仍可能带上的 Markdown 包裹和结尾分号。
     */
    private String sanitizeSql(String rawSql) {
        String cleanSql = rawSql == null ? "" : rawSql
                .replace("```sql", "")
                .replace("```", "")
                .replace("\n", " ")
                .trim();
        if (cleanSql.endsWith(";")) {
            cleanSql = cleanSql.substring(0, cleanSql.length() - 1);
        }
        return cleanSql;
    }
}

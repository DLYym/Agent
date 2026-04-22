package com.yk.demoai.service;

import com.yk.demoai.dto.SqlRequest;
import com.yk.demoai.model.AgentWorkflowResult;
import com.yk.demoai.model.AgentWorkflowStep;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Text-to-SQL 查询、执行与连通性检测的对外服务接口。
 */
public interface ISqlService {

    /**
     * 先执行 reasoning 阶段，只展示分析与检索轨迹，等待用户确认后再生成 SQL。
     */
    Map<String, Object> previewSqlWorkflow(SqlRequest request);

    /**
     * 根据自然语言问题生成只读 SQL，但不直接执行。
     */
    Map<String, Object> generateSql(SqlRequest request);

    /**
     * 执行流式分析，并在每个关键步骤完成后把结果回调给调用方。
     */
    AgentWorkflowResult analyzeSqlWorkflow(SqlRequest request, AgentWorkflowStreamListener streamListener);

    /**
     * 兼容只关心步骤回调的旧调用方。
     */
    default AgentWorkflowResult analyzeSqlWorkflow(SqlRequest request, Consumer<AgentWorkflowStep> stepListener) {
        return analyzeSqlWorkflow(request, AgentWorkflowStreamListener.fromStepListener(stepListener));
    }

    /**
     * 执行前端确认后的只读 SQL。
     */
    Map<String, Object> executeSql(SqlRequest request);

    /**
     * 测试单个或多个数据源是否可以正常连通并提取 Schema。
     */
    Map<String, String> testConnection(SqlRequest request);

    /**
     * 连接数据库实例并返回可供手动选择的数据库列表。
     */
    Map<String, Object> listDatabases(SqlRequest request);
}

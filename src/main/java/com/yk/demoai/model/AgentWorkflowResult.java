package com.yk.demoai.model;

import java.util.List;
import java.util.Map;

/**
 * 汇总 ReAct 工作流的最终产物及中间步骤信息。
 */
public record AgentWorkflowResult(
        DatasourceDescriptor targetDatasource,
        String sql,
        List<Map<String, Object>> data,
        List<String> matchedTables,
        WorkflowTokenUsage tokenUsage,
        List<AgentWorkflowStep> steps,
        int attempts,
        String workflowStatus,
        AgentPendingAction pendingAction
) {
}

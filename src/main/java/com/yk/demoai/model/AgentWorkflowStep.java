package com.yk.demoai.model;

import java.util.Map;

/**
 * 描述 ReAct 工作流中的单个可观测步骤，便于前端或日志展示执行轨迹。
 */
public record AgentWorkflowStep(
        String code,
        String title,
        String status,
        String summary,
        Map<String, Object> details        
) {
}

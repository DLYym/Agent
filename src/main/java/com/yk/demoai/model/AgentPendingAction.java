package com.yk.demoai.model;

/**
 * 描述当前工作流在关键节点上等待用户确认的下一步动作。
 */
public record AgentPendingAction(
        String code,
        String title,
        String description,
        String buttonLabel
) {
}

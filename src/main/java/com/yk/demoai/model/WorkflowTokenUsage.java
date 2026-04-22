package com.yk.demoai.model;

import dev.langchain4j.model.output.TokenUsage;

/**
 * 汇总一次 Agent 分析过程中消耗的 token 数，方便前端直接展示。
 */
public record WorkflowTokenUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens
) {

    public static WorkflowTokenUsage empty() {
        return new WorkflowTokenUsage(0, 0, 0);
    }

    public static WorkflowTokenUsage from(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return empty();
        }
        return new WorkflowTokenUsage(
                safeValue(tokenUsage.inputTokenCount()),
                safeValue(tokenUsage.outputTokenCount()),
                safeValue(tokenUsage.totalTokenCount())
        );
    }

    public WorkflowTokenUsage add(WorkflowTokenUsage other) {
        if (other == null) {
            return this;
        }
        return new WorkflowTokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                totalTokens + other.totalTokens
        );
    }

    private static int safeValue(Integer value) {
        return value == null ? 0 : value;
    }
}

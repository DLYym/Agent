package com.yk.demoai.service;

import com.yk.demoai.model.AgentWorkflowStep;
import com.yk.demoai.model.WorkflowTokenUsage;

import java.util.function.Consumer;

/**
 * 聚合工作流步骤和 LLM token 回调，供流式接口把生成过程实时推给前端。
 */
public interface AgentWorkflowStreamListener {

    /**
     * 默认空实现，适合非流式场景直接复用。
     */
    AgentWorkflowStreamListener NO_OP = new AgentWorkflowStreamListener() {
    };

    /**
     * 是否需要把 LLM 增量 token 继续向外透传。
     */
    default boolean streamLlmTokens() {
        return false;
    }

    /**
     * 工作流步骤回调。
     */
    default void onStep(AgentWorkflowStep step) {
    }

    /**
     * 某个 LLM 生成阶段开始。
     */
    default void onLlmStart(String phaseCode, String phaseTitle, int attempt) {
    }

    /**
     * 某个 LLM 生成阶段输出了新的 token 增量。
     */
    default void onLlmToken(String phaseCode, String phaseTitle, int attempt, String token) {
    }

    /**
     * 某个 LLM 生成阶段结束，并返回完整文本。
     */
    default void onLlmComplete(String phaseCode, String phaseTitle, int attempt, String fullText) {
    }

    /**
     * 某个 LLM 生成阶段结束后返回的 token 消耗统计。
     */
    default void onLlmUsage(String phaseCode, String phaseTitle, int attempt, WorkflowTokenUsage tokenUsage) {
    }

    /**
     * 兼容只关心步骤流的旧调用方。
     */
    static AgentWorkflowStreamListener fromStepListener(Consumer<AgentWorkflowStep> stepListener) {
        return new AgentWorkflowStreamListener() {
            @Override
            public void onStep(AgentWorkflowStep step) {
                stepListener.accept(step);
            }
        };
    }
}

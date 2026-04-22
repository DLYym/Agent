package com.yk.demoai.exception;

import com.yk.demoai.model.AgentWorkflowStep;

import java.util.List;

/**
 * 在 ReAct 工作流无法继续修复时抛出，便于把步骤轨迹一并回传给调用方。
 */
public class AgentWorkflowException extends RuntimeException {

    private final List<AgentWorkflowStep> steps;
    private final String lastSql;

    public AgentWorkflowException(String message, List<AgentWorkflowStep> steps, String lastSql, Throwable cause) {
        super(message, cause);
        this.steps = List.copyOf(steps);
        this.lastSql = lastSql;
    }

    public AgentWorkflowException(String message, List<AgentWorkflowStep> steps, String lastSql) {
        this(message, steps, lastSql, null);
    }

    public List<AgentWorkflowStep> getSteps() {
        return steps;
    }

    public String getLastSql() {
        return lastSql;
    }
}

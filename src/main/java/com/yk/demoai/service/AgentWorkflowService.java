package com.yk.demoai.service;

import com.yk.demoai.model.AgentWorkflowResult;
import com.yk.demoai.model.AgentWorkflowStep;
import com.yk.demoai.model.DatasourceDescriptor;

import java.util.List;
import java.util.function.Consumer;

/**
 * 负责执行 Text-to-SQL 的 ReAct 工作流。
 */
public interface AgentWorkflowService {

    /**
     * 只执行规划、检索和上下文分析，并在生成 SQL 前等待用户授权。
     */
    AgentWorkflowResult previewWorkflow(String question,
                                        List<DatasourceDescriptor> datasources,
                                        String targetDatasourceId);

    /**
     * 在用户确认上下文后生成 SQL，并在执行前再次交由用户确认。
     */
    default AgentWorkflowResult runDraftWorkflow(String question,
                                                 List<DatasourceDescriptor> datasources,
                                                 String targetDatasourceId) {
        return runDraftWorkflow(question, datasources, targetDatasourceId, AgentWorkflowStreamListener.NO_OP);
    }

    /**
     * 生成 SQL 时把每个阶段的步骤实时回调出去，便于前端做流式思考过程展示。
     */
    AgentWorkflowResult runDraftWorkflow(String question,
                                         List<DatasourceDescriptor> datasources,
                                         String targetDatasourceId,
                                         AgentWorkflowStreamListener streamListener);

    /**
     * 兼容只关心步骤回调的旧调用方。
     */
    default AgentWorkflowResult runDraftWorkflow(String question,
                                                 List<DatasourceDescriptor> datasources,
                                                 String targetDatasourceId,
                                                 Consumer<AgentWorkflowStep> stepListener) {
        return runDraftWorkflow(
                question,
                datasources,
                targetDatasourceId,
                AgentWorkflowStreamListener.fromStepListener(stepListener)
        );
    }

    /**
     * 基于用户问题在候选数据源上完成计划、检索、SQL 生成、校验、修复与执行。
     */
    AgentWorkflowResult runGenerateWorkflow(String question,
                                            List<DatasourceDescriptor> datasources,
                                            String targetDatasourceId);
}

package com.yk.demoai.service;

import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaRetrievalContext;

import java.util.List;

/**
 * 负责执行 Schema 级别的混合召回，并选择最合适的数据源。
 */
public interface SchemaHybridRetriever {

    /**
     * 基于用户问题在多个数据源中完成 Schema 检索和目标库选择。
     */
    SchemaRetrievalContext retrieve(String question, List<DatasourceDescriptor> datasources, String targetDatasourceId);
}

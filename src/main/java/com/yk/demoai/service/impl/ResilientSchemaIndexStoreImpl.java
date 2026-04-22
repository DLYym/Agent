package com.yk.demoai.service.impl;

import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaDocument;
import com.yk.demoai.model.SchemaSearchHit;
import com.yk.demoai.service.SchemaIndexStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

/**
 * 为 ElasticSearch 索引能力提供内存兜底，确保本地和异常场景仍可工作。
 */
@Slf4j
@RequiredArgsConstructor
public class ResilientSchemaIndexStoreImpl implements SchemaIndexStore {

    private final SchemaIndexStore primary;
    private final SchemaIndexStore fallback;
    private final boolean allowFallback;

    @Override
    public void replaceDatasourceDocuments(DatasourceDescriptor datasource, List<SchemaDocument> documents) {
        fallback.replaceDatasourceDocuments(datasource, documents);
        try {
            primary.replaceDatasourceDocuments(datasource, documents);
        } catch (RuntimeException ex) {
            if (!allowFallback) {
                throw ex;
            }
            log.warn("ElasticSearch 索引失败，已回退到内存检索: {}", ex.getMessage());
        }
    }

    @Override
    public List<SchemaSearchHit> vectorSearch(String question, List<String> datasourceIds, int limit) {
        return executeWithFallback(() -> primary.vectorSearch(question, datasourceIds, limit),
                () -> fallback.vectorSearch(question, datasourceIds, limit));
    }

    @Override
    public List<SchemaSearchHit> keywordSearch(String question, List<String> datasourceIds, int limit) {
        return executeWithFallback(() -> primary.keywordSearch(question, datasourceIds, limit),
                () -> fallback.keywordSearch(question, datasourceIds, limit));
    }

    private List<SchemaSearchHit> executeWithFallback(Supplier<List<SchemaSearchHit>> primaryAction,
                                                      Supplier<List<SchemaSearchHit>> fallbackAction) {
        try {
            return primaryAction.get();
        } catch (RuntimeException ex) {
            if (!allowFallback) {
                throw ex;
            }
            log.warn("ElasticSearch 查询失败，切换到内存检索: {}", ex.getMessage());
            return fallbackAction.get();
        }
    }
}

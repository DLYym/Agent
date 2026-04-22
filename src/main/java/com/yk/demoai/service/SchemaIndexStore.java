package com.yk.demoai.service;

import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaSearchHit;
import com.yk.demoai.model.SchemaDocument;

import java.util.List;

/**
 * 抽象 Schema 文档索引能力，便于在内存和 ElasticSearch 之间切换。
 */
public interface SchemaIndexStore {

    /**
     * 使用最新文档全量替换某个数据源在索引中的内容。
     */
    void replaceDatasourceDocuments(DatasourceDescriptor datasource, List<SchemaDocument> documents);

    /**
     * 执行向量召回。
     */
    List<SchemaSearchHit> vectorSearch(String question, List<String> datasourceIds, int limit);

    /**
     * 执行关键字召回。
     */
    List<SchemaSearchHit> keywordSearch(String question, List<String> datasourceIds, int limit);
}

package com.yk.demoai.service;

import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaSnapshot;

/**
 * 从目标数据库中抽取表结构、注释和字段信息。
 */
public interface DatabaseSchemaExtractor {

    /**
     * 拉取指定数据源的 Schema 快照。
     */
    SchemaSnapshot extract(DatasourceDescriptor descriptor);
}

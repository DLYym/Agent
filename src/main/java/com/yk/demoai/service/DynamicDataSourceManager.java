package com.yk.demoai.service;

import com.yk.demoai.model.DatasourceDescriptor;

import javax.sql.DataSource;

/**
 * 负责按需获取和维护动态数据源。
 */
public interface DynamicDataSourceManager {

    /**
     * 根据数据源描述获取连接池实例，不存在时按需创建。
     */
    DataSource getDataSource(DatasourceDescriptor descriptor);

    /**
     * 返回缓存中数据源的大致数量，主要用于测试和观测。
     */
    long estimatedSize();

    /**
     * 主动触发缓存清理。
     */
    void cleanUp();

    /**
     * 清空当前缓存中的所有数据源。
     */
    void invalidateAll();
}

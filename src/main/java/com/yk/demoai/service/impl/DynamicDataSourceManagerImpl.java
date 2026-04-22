package com.yk.demoai.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.service.DynamicDataSourceManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 使用 Caffeine 缓存动态数据源，避免频繁切库时重复创建连接池。
 */
@Slf4j
@Service
public class DynamicDataSourceManagerImpl implements DynamicDataSourceManager {

    private final Cache<String, DataSource> dataSourceCache;
    private final AppProperties properties;

    public DynamicDataSourceManagerImpl(AppProperties properties) {
        this.properties = properties;
        this.dataSourceCache = Caffeine.newBuilder()
                .maximumSize(properties.getDatasourceCache().getMaxSize())
                .expireAfterAccess(properties.getDatasourceCache().getExpireAfterAccess())
                .removalListener(this::closeDataSource)
                .build();
    }

    @Override
    public DataSource getDataSource(DatasourceDescriptor descriptor) {
        return dataSourceCache.get(descriptor.cacheKey(), key -> createDataSource(descriptor));
    }

    @Override
    public long estimatedSize() {
        return dataSourceCache.estimatedSize();
    }

    @Override
    public void cleanUp() {
        dataSourceCache.cleanUp();
    }

    @Override
    public void invalidateAll() {
        dataSourceCache.invalidateAll();
        dataSourceCache.cleanUp();
    }

    /**
     * Spring 关闭容器时主动释放缓存里的连接池，避免关闭过程遗留数据库连接。
     */
    @PreDestroy
    public void destroy() {
        invalidateAll();
    }

    private DataSource createDataSource(DatasourceDescriptor descriptor) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(descriptor.jdbcUrl());
        config.setUsername(descriptor.username());
        config.setPassword(descriptor.password());
        config.setPoolName("text2sql-" + Math.abs(descriptor.cacheKey().hashCode()));
        config.setMaximumPoolSize(properties.getDatasourceCache().getMaxPoolSize());
        config.setMinimumIdle(properties.getDatasourceCache().getMinIdle());
        config.setIdleTimeout(properties.getDatasourceCache().getIdleTimeout().toMillis());
        config.setConnectionTimeout(properties.getDatasourceCache().getConnectionTimeout().toMillis());
        config.setValidationTimeout(properties.getDatasourceCache().getValidationTimeout().toMillis());
        config.setInitializationFailTimeout(-1);
        config.setReadOnly(true);

        String driverClassName = resolveDriverClassName(descriptor);
        if (StringUtils.hasText(driverClassName)) {
            config.setDriverClassName(driverClassName);
        }

        log.info("创建数据源缓存: {} ({})", descriptor.displayName(), descriptor.jdbcUrl());
        return new HikariDataSource(config);
    }

    private String resolveDriverClassName(DatasourceDescriptor descriptor) {
        if (StringUtils.hasText(descriptor.driverClassName())) {
            return descriptor.driverClassName();
        }
        try {
            return DatabaseDriver.fromJdbcUrl(descriptor.jdbcUrl()).getDriverClassName();
        } catch (IllegalArgumentException ex) {
            log.warn("无法根据 JDBC URL 自动识别驱动: {}", descriptor.jdbcUrl());
            return null;
        }
    }

    private void closeDataSource(String key, DataSource dataSource, RemovalCause cause) {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            hikariDataSource.close();
            log.info("数据源缓存淘汰: key={}, cause={}", safeKeyForLog(key), cause);
        }
    }

    private String safeKeyForLog(String key) {
        return "ds#" + Integer.toHexString(StringUtils.hasText(key) ? key.hashCode() : 0);
    }
}

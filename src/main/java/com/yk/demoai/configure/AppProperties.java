package com.yk.demoai.configure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 集中管理 RAG、ElasticSearch 和动态数据源缓存相关配置。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Rag rag = new Rag();

    private final Elasticsearch elasticsearch = new Elasticsearch();

    private final DatasourceCache datasourceCache = new DatasourceCache();

    private final Workflow workflow = new Workflow();

    /**
     * Schema 检索的召回参数。
     */
    @Data
    public static class Rag {
        private int schemaTopK = 6;
        /**
         * 从宽召回结果里再收敛出少量高置信表，减少把无关表一并塞给模型。
         */
        private int schemaFocusTopK = 3;
        private double vectorWeight = 0.65;
        private double keywordWeight = 0.35;
    }

    /**
     * ElasticSearch 连接和回退策略配置。
     */
    @Data
    public static class Elasticsearch {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:9200";
        private String indexName = "text2sql_schema";
        private String username;
        private String password;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        private boolean allowInMemoryFallback = true;
    }

    /**
     * 动态连接池缓存的容量和超时配置。
     */
    @Data
    public static class DatasourceCache {
        private long maxSize = 32;
        private Duration expireAfterAccess = Duration.ofMinutes(30);
        private int maxPoolSize = 5;
        private int minIdle = 1;
        private Duration idleTimeout = Duration.ofSeconds(60);
        private Duration connectionTimeout = Duration.ofSeconds(5);
        private Duration validationTimeout = Duration.ofSeconds(2);
    }

    /**
     * Agent 工作流的迭代与自动执行配置。
     */
    @Data
    public static class Workflow {
        private int maxRepairAttempts = 2;
        private boolean autoExecuteGeneratedSql = true;
        /**
         * 真正的 token streaming 需要等待模型持续回流 token，这里限制单轮最大等待时间。
         */
        private Duration llmStreamTimeout = Duration.ofMinutes(2);
    }
}

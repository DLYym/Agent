package com.yk.demoai.service;

import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaRetrievalContext;
import com.yk.demoai.service.impl.DatabaseSchemaExtractorImpl;
import com.yk.demoai.service.impl.DynamicDataSourceManagerImpl;
import com.yk.demoai.service.impl.InMemorySchemaIndexStoreImpl;
import com.yk.demoai.service.impl.SchemaDocumentAssemblerImpl;
import com.yk.demoai.service.impl.SchemaHybridRetrieverImpl;
import com.yk.demoai.testutil.TestEmbeddingModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaHybridRetrieverTest {

    private final DynamicDataSourceManager dataSourceManager = new DynamicDataSourceManagerImpl(new AppProperties());

    @AfterEach
    void tearDown() {
        dataSourceManager.invalidateAll();
    }

    @Test
    void shouldPreferExactTableNameWhenHybridRecallRunsAcrossDatasources() throws Exception {
        DatasourceDescriptor sales = descriptor("sales",
                "jdbc:h2:mem:sales_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        DatasourceDescriptor inventory = descriptor("inventory",
                "jdbc:h2:mem:inventory_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");

        initializeSchema(sales, """
                CREATE TABLE orders (
                    id BIGINT PRIMARY KEY,
                    amount DECIMAL(10, 2)
                )
                """, "COMMENT ON TABLE orders IS '订单表'");
        initializeSchema(inventory, """
                CREATE TABLE stock_items (
                    id BIGINT PRIMARY KEY,
                    sku VARCHAR(64)
                )
                """, "COMMENT ON TABLE stock_items IS '库存表'");

        AppProperties properties = new AppProperties();
        SchemaIndexStore indexStore = new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel());
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                indexStore,
                properties
        );

        SchemaRetrievalContext context = retriever.retrieve(
                "请统计 orders 表里的订单数量",
                List.of(sales, inventory),
                null
        );

        assertEquals("sales", context.targetDatasource().id());
        assertFalse(context.hits().isEmpty());
        assertEquals("orders", context.hits().getFirst().document().tableName());
        assertTrue(context.schemaContext().contains("表名: orders"));
    }

    @Test
    void shouldPreferCoreOrderTablesOverNoiseTablesForChineseQuestion() throws Exception {
        DatasourceDescriptor sales = descriptor("sales",
                "jdbc:h2:mem:noise_guard_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");

        try (Connection connection = dataSourceManager.getDataSource(sales).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE t_orders (
                        order_id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        pay_amount DECIMAL(10, 2) NOT NULL
                    )
                    """);
            statement.execute("COMMENT ON TABLE t_orders IS '核心表-订单主表'");
            statement.execute("COMMENT ON COLUMN t_orders.user_id IS '下单用户ID'");
            statement.execute("COMMENT ON COLUMN t_orders.pay_amount IS '实付金额'");

            statement.execute("""
                    CREATE TABLE t_users (
                        user_id BIGINT PRIMARY KEY,
                        username VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("COMMENT ON TABLE t_users IS '核心表-用户信息表'");
            statement.execute("COMMENT ON COLUMN t_users.username IS '用户名'");

            statement.execute("""
                    CREATE TABLE t_marketing_campaign_10 (
                        id BIGINT PRIMARY KEY,
                        related_user VARCHAR(64),
                        related_amount DECIMAL(10, 2)
                    )
                    """);
            statement.execute("COMMENT ON TABLE t_marketing_campaign_10 IS '干扰表-营销活动归档表'");
            statement.execute("COMMENT ON COLUMN t_marketing_campaign_10.related_user IS '关联人员'");
            statement.execute("COMMENT ON COLUMN t_marketing_campaign_10.related_amount IS '关联金额'");
        }

        AppProperties properties = new AppProperties();
        properties.getRag().setSchemaFocusTopK(3);
        SchemaIndexStore indexStore = new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel());
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                indexStore,
                properties
        );

        SchemaRetrievalContext context = retriever.retrieve(
                "查找订单金额最高的前10位客户",
                List.of(sales),
                "sales"
        );

        assertFalse(context.hits().isEmpty());
        assertEquals("t_orders", context.hits().getFirst().document().tableName());
        assertFalse(context.hits().stream()
                .map(hit -> hit.document().tableName())
                .toList()
                .contains("t_marketing_campaign_10"));
    }

    private void initializeSchema(DatasourceDescriptor descriptor, String ddl, String commentSql) throws Exception {
        try (Connection connection = dataSourceManager.getDataSource(descriptor).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(ddl);
            statement.execute(commentSql);
        }
    }

    private DatasourceDescriptor descriptor(String id, String url) {
        return new DatasourceDescriptor(id, id, url, "sa", "", null, null, null, List.of());
    }
}

package com.yk.demoai.service;

import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.model.ColumnSchema;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaSnapshot;
import com.yk.demoai.model.TableSchema;
import com.yk.demoai.service.impl.DatabaseSchemaExtractorImpl;
import com.yk.demoai.service.impl.DynamicDataSourceManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaExtractorTest {

    private final DynamicDataSourceManager dataSourceManager = new DynamicDataSourceManagerImpl(new AppProperties());
    private final DatabaseSchemaExtractor extractor = new DatabaseSchemaExtractorImpl(dataSourceManager);

    @AfterEach
    void tearDown() {
        dataSourceManager.invalidateAll();
    }

    @Test
    void shouldExtractTableAndColumnComments() throws Exception {
        DatasourceDescriptor descriptor = new DatasourceDescriptor(
                "schema-test",
                "schema-test",
                "jdbc:h2:mem:schema_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "sa",
                "",
                null,
                null,
                null,
                List.of()
        );

        try (Connection connection = dataSourceManager.getDataSource(descriptor).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        amount DECIMAL(10, 2) NOT NULL,
                        customer_name VARCHAR(64)
                    )
                    """);
            statement.execute("COMMENT ON TABLE orders IS '订单表'");
            statement.execute("COMMENT ON COLUMN orders.amount IS '订单金额'");
        }

        SchemaSnapshot snapshot = extractor.extract(descriptor);
        TableSchema table = snapshot.tables().stream()
                .filter(item -> item.tableName().equalsIgnoreCase("orders"))
                .findFirst()
                .orElseThrow();
        ColumnSchema amountColumn = table.columns().stream()
                .filter(column -> column.name().equalsIgnoreCase("amount"))
                .findFirst()
                .orElseThrow();

        assertTrue(snapshot.databaseProductName().contains("H2"));
        assertEquals("订单表", table.tableComment());
        assertEquals("订单金额", amountColumn.comment());
        assertTrue(table.columns().stream().anyMatch(ColumnSchema::primaryKey));
    }
}

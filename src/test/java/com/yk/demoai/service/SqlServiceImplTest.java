package com.yk.demoai.service;

import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.dto.SqlRequest;
import com.yk.demoai.model.AgentWorkflowResult;
import com.yk.demoai.model.SchemaRetrievalContext;
import com.yk.demoai.service.impl.AgentWorkflowServiceImpl;
import com.yk.demoai.service.impl.DatabaseSchemaExtractorImpl;
import com.yk.demoai.service.DatabaseTypeAdapter;
import com.yk.demoai.service.impl.DynamicDataSourceManagerImpl;
import com.yk.demoai.service.impl.InMemorySchemaIndexStoreImpl;
import com.yk.demoai.service.impl.ReadOnlySqlExecutorImpl;
import com.yk.demoai.service.impl.SchemaDocumentAssemblerImpl;
import com.yk.demoai.service.impl.SchemaHybridRetrieverImpl;
import com.yk.demoai.service.impl.SqlServiceImpl;
import com.yk.demoai.testutil.TestEmbeddingModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServiceImplTest {

    private final DynamicDataSourceManager dataSourceManager = new DynamicDataSourceManagerImpl(new AppProperties());

    @AfterEach
    void tearDown() {
        dataSourceManager.invalidateAll();
    }

    @Test
    void shouldPreviewWorkflowBeforeGeneration() throws Exception {
        String url = "jdbc:h2:mem:sql_service_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (Connection connection = dataSourceManager.getDataSource(new com.yk.demoai.model.DatasourceDescriptor(
                "sales", "sales", url, "sa", "", null, null, null, List.of())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        amount DECIMAL(10, 2)
                    )
                    """);
            statement.execute("COMMENT ON TABLE orders IS '订单表'");
            statement.execute("INSERT INTO orders(id, amount) VALUES (1, 99.00), (2, 188.00)");
        }

        AppProperties properties = new AppProperties();
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        ReadOnlySqlExecutor executor = new ReadOnlySqlExecutorImpl(dataSourceManager, new DatabaseTypeAdapter());
        SqlGenerator sqlGenerator = (schema, dbType, question) -> "SELECT COUNT(*) AS total FROM orders";
        SqlRepairGenerator sqlRepairGenerator = (schema, dbType, question, previousSql, feedback) -> "SELECT COUNT(*) AS total FROM orders";
        AgentWorkflowService agentWorkflowService = new AgentWorkflowServiceImpl(
                sqlGenerator,
                sqlRepairGenerator,
                (schema, dbType, question) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                (schema, dbType, question, previousSql, feedback) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                retriever,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                properties
        );

        SqlServiceImpl sqlService = new SqlServiceImpl(
                agentWorkflowService,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                dataSourceManager,
                new DatabaseTypeAdapter()
        );

        SqlRequest request = new SqlRequest();
        request.setTargetDatasourceId("sales");
        request.setDbUrl(url);
        request.setUsername("sa");
        request.setPassword("");
        request.setQuestion("订单总数是多少");

        Map<String, Object> response = sqlService.previewSqlWorkflow(request);

        assertEquals("sales", response.get("datasourceId"));
        assertEquals("awaiting_generation_approval", response.get("workflowStatus"));
        assertNotNull(response.get("matchedTables"));
        assertNotNull(response.get("steps"));
        assertNotNull(response.get("pendingAction"));
    }

    @Test
    void shouldGenerateSqlAndRequireManualExecution() throws Exception {
        String url = "jdbc:h2:mem:sql_generate_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (Connection connection = dataSourceManager.getDataSource(new com.yk.demoai.model.DatasourceDescriptor(
                "sales", "sales", url, "sa", "", null, null, null, List.of())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        amount DECIMAL(10, 2)
                    )
                    """);
            statement.execute("COMMENT ON TABLE orders IS '订单表'");
            statement.execute("INSERT INTO orders(id, amount) VALUES (1, 99.00), (2, 188.00)");
        }

        AppProperties properties = new AppProperties();
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        ReadOnlySqlExecutor executor = new ReadOnlySqlExecutorImpl(dataSourceManager, new DatabaseTypeAdapter());
        SqlGenerator sqlGenerator = (schema, dbType, question) -> "SELECT COUNT(*) AS total FROM orders";
        SqlRepairGenerator sqlRepairGenerator = (schema, dbType, question, previousSql, feedback) -> "SELECT COUNT(*) AS total FROM orders";
        AgentWorkflowService agentWorkflowService = new AgentWorkflowServiceImpl(
                sqlGenerator,
                sqlRepairGenerator,
                (schema, dbType, question) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                (schema, dbType, question, previousSql, feedback) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                retriever,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                properties
        );

        SqlServiceImpl sqlService = new SqlServiceImpl(
                agentWorkflowService,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                dataSourceManager,
                new DatabaseTypeAdapter()
        );

        SqlRequest request = new SqlRequest();
        request.setTargetDatasourceId("sales");
        request.setDbUrl(url);
        request.setUsername("sa");
        request.setPassword("");
        request.setQuestion("订单总数是多少");

        Map<String, Object> response = sqlService.generateSql(request);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

        assertEquals("sales", response.get("datasourceId"));
        assertEquals("SELECT COUNT(*) AS total FROM orders", response.get("sql"));
        assertEquals(0, data.size());
        assertEquals("ready_for_execution", response.get("workflowStatus"));
        assertNotNull(response.get("steps"));
        assertNotNull(response.get("pendingAction"));
    }

    @Test
    void shouldExecuteSqlLocally() {
        String url = "jdbc:h2:mem:execute_sql_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        AppProperties properties = new AppProperties();
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        ReadOnlySqlExecutor executor = new ReadOnlySqlExecutorImpl(dataSourceManager, new DatabaseTypeAdapter());
        SqlGenerator sqlGenerator = (schema, dbType, question) -> "SELECT 1";
        SqlRepairGenerator sqlRepairGenerator = (schema, dbType, question, previousSql, feedback) -> "SELECT 1";
        AgentWorkflowService agentWorkflowService = new AgentWorkflowServiceImpl(
                sqlGenerator,
                sqlRepairGenerator,
                (schema, dbType, question) -> fixedTokenStream("SELECT 1"),
                (schema, dbType, question, previousSql, feedback) -> fixedTokenStream("SELECT 1"),
                retriever,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                properties
        );

        SqlServiceImpl sqlService = new SqlServiceImpl(
                agentWorkflowService,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                dataSourceManager,
                new DatabaseTypeAdapter()
        );

        try (Connection connection = dataSourceManager.getDataSource(new com.yk.demoai.model.DatasourceDescriptor(
                "sales", "sales", url, "sa", "", null, null, null, List.of())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        amount DECIMAL(10, 2)
                    )
                    """);
            statement.execute("INSERT INTO orders(id, amount) VALUES (1, 99.00), (2, 188.00)");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        SqlRequest request = new SqlRequest();
        request.setTargetDatasourceId("sales");
        request.setDbUrl(url);
        request.setUsername("sa");
        request.setPassword("");
        request.setSql("SELECT COUNT(*) AS total FROM orders");

        Map<String, Object> response = sqlService.executeSql(request);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) response.get("columns");

        assertEquals(1, data.size());
        assertEquals(2, ((Number) data.getFirst().values().iterator().next()).intValue());
        assertEquals(List.of("total"), columns);
        assertEquals("SQL 执行成功，返回 1 条记录。", response.get("message"));
    }

    @Test
    void shouldLimitMatchedTablesToFocusedSubset() throws Exception {
        String url = "jdbc:h2:mem:focus_tables_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (Connection connection = dataSourceManager.getDataSource(new com.yk.demoai.model.DatasourceDescriptor(
                "sales", "sales", url, "sa", "", null, null, null, List.of())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT, amount DECIMAL(10, 2))");
            statement.execute("CREATE TABLE order_items (id BIGINT PRIMARY KEY, order_id BIGINT, product_id BIGINT)");
            statement.execute("CREATE TABLE payments (id BIGINT PRIMARY KEY, order_id BIGINT, pay_amount DECIMAL(10, 2))");
            statement.execute("CREATE TABLE refunds (id BIGINT PRIMARY KEY, order_id BIGINT, refund_amount DECIMAL(10, 2))");
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username VARCHAR(64))");
        }

        AppProperties properties = new AppProperties();
        properties.getRag().setSchemaTopK(6);
        properties.getRag().setSchemaFocusTopK(3);

        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        SchemaRetrievalContext context = retriever.retrieve(
                "orders order_items payments refunds users revenue ranking",
                List.of(new com.yk.demoai.model.DatasourceDescriptor("sales", "sales", url, "sa", "", null, null, null, List.of())),
                "sales"
        );

        assertTrue(context.hits().size() <= 3);
        assertTrue(context.hits().stream()
                .map(hit -> hit.document().tableName())
                .distinct()
                .count() <= 3);
    }

    @Test
    void shouldListDatabasesForConnectedInstance() {
        String url = "jdbc:h2:mem:list_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        AppProperties properties = new AppProperties();
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        ReadOnlySqlExecutor executor = new ReadOnlySqlExecutorImpl(dataSourceManager, new DatabaseTypeAdapter());
        SqlGenerator sqlGenerator = (schema, dbType, question) -> "SELECT 1";
        SqlRepairGenerator sqlRepairGenerator = (schema, dbType, question, previousSql, feedback) -> "SELECT 1";
        AgentWorkflowService agentWorkflowService = new AgentWorkflowServiceImpl(
                sqlGenerator,
                sqlRepairGenerator,
                (schema, dbType, question) -> fixedTokenStream("SELECT 1"),
                (schema, dbType, question, previousSql, feedback) -> fixedTokenStream("SELECT 1"),
                retriever,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                properties
        );

        SqlServiceImpl sqlService = new SqlServiceImpl(
                agentWorkflowService,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                dataSourceManager,
                new DatabaseTypeAdapter()
        );

        SqlRequest request = new SqlRequest();
        request.setDbUrl(url);
        request.setUsername("sa");
        request.setPassword("");

        Map<String, Object> response = sqlService.listDatabases(request);

        assertEquals("success", response.get("status"));
        assertNotNull(response.get("databases"));
    }

    @Test
    void shouldRepairGeneratedSqlAfterValidationFailure() throws Exception {
        String url = "jdbc:h2:mem:repair_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (Connection connection = dataSourceManager.getDataSource(new com.yk.demoai.model.DatasourceDescriptor(
                "sales", "sales", url, "sa", "", null, null, null, List.of())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        amount DECIMAL(10, 2)
                    )
                    """);
            statement.execute("COMMENT ON TABLE orders IS '订单表'");
            statement.execute("INSERT INTO orders(id, amount) VALUES (1, 99.00), (2, 188.00)");
        }

        AppProperties properties = new AppProperties();
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        ReadOnlySqlExecutor executor = new ReadOnlySqlExecutorImpl(dataSourceManager, new DatabaseTypeAdapter());
        SqlGenerator sqlGenerator = (schema, dbType, question) -> "SELECT COUNT(*) AS total FROM missing_orders";
        SqlRepairGenerator sqlRepairGenerator = (schema, dbType, question, previousSql, feedback) ->
                "SELECT COUNT(*) AS total FROM orders";
        AgentWorkflowService agentWorkflowService = new AgentWorkflowServiceImpl(
                sqlGenerator,
                sqlRepairGenerator,
                (schema, dbType, question) -> fixedTokenStream("SELECT COUNT(*) AS total FROM missing_orders"),
                (schema, dbType, question, previousSql, feedback) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                retriever,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                properties
        );

        SqlServiceImpl sqlService = new SqlServiceImpl(
                agentWorkflowService,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                dataSourceManager,
                new DatabaseTypeAdapter()
        );

        SqlRequest request = new SqlRequest();
        request.setTargetDatasourceId("sales");
        request.setDbUrl(url);
        request.setUsername("sa");
        request.setPassword("");
        request.setQuestion("订单总数是多少");

        Map<String, Object> response = sqlService.generateSql(request);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        @SuppressWarnings("unchecked")
        List<?> steps = (List<?>) response.get("steps");

        assertEquals("SELECT COUNT(*) AS total FROM orders", response.get("sql"));
        assertEquals(0, data.size());
        assertNotNull(steps);
        assertEquals(2, response.get("attempts"));
        assertEquals("ready_for_execution", response.get("workflowStatus"));
    }

    @Test
    void shouldStreamSqlTokensDuringAnalyzeWorkflow() throws Exception {
        String url = "jdbc:h2:mem:streaming_db;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        try (Connection connection = dataSourceManager.getDataSource(new com.yk.demoai.model.DatasourceDescriptor(
                "sales", "sales", url, "sa", "", null, null, null, List.of())).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE orders (
                        id BIGINT PRIMARY KEY,
                        amount DECIMAL(10, 2)
                    )
                    """);
            statement.execute("COMMENT ON TABLE orders IS '订单表'");
        }

        AppProperties properties = new AppProperties();
        SchemaHybridRetriever retriever = new SchemaHybridRetrieverImpl(
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                new SchemaDocumentAssemblerImpl(),
                new InMemorySchemaIndexStoreImpl(new TestEmbeddingModel()),
                properties
        );
        ReadOnlySqlExecutor executor = new ReadOnlySqlExecutorImpl(dataSourceManager, new DatabaseTypeAdapter());
        SqlGenerator sqlGenerator = (schema, dbType, question) -> "SELECT COUNT(*) AS total FROM orders";
        SqlRepairGenerator sqlRepairGenerator = (schema, dbType, question, previousSql, feedback) -> "SELECT COUNT(*) AS total FROM orders";
        AgentWorkflowService agentWorkflowService = new AgentWorkflowServiceImpl(
                sqlGenerator,
                sqlRepairGenerator,
                (schema, dbType, question) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                (schema, dbType, question, previousSql, feedback) -> fixedTokenStream("SELECT COUNT(*) AS total FROM orders"),
                retriever,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                properties
        );

        SqlServiceImpl sqlService = new SqlServiceImpl(
                agentWorkflowService,
                executor,
                new DatabaseSchemaExtractorImpl(dataSourceManager),
                dataSourceManager,
                new DatabaseTypeAdapter()
        );

        SqlRequest request = new SqlRequest();
        request.setTargetDatasourceId("sales");
        request.setDbUrl(url);
        request.setUsername("sa");
        request.setPassword("");
        request.setQuestion("订单总数是多少");

        StringBuilder tokenBuffer = new StringBuilder();
        AtomicReference<String> completedSql = new AtomicReference<>("");
        AgentWorkflowResult result = sqlService.analyzeSqlWorkflow(request, new AgentWorkflowStreamListener() {
            @Override
            public boolean streamLlmTokens() {
                return true;
            }

            @Override
            public void onLlmToken(String phaseCode, String phaseTitle, int attempt, String token) {
                tokenBuffer.append(token);
            }

            @Override
            public void onLlmComplete(String phaseCode, String phaseTitle, int attempt, String fullText) {
                completedSql.set(fullText);
            }
        });

        assertEquals("SELECT COUNT(*) AS total FROM orders", tokenBuffer.toString());
        assertEquals("SELECT COUNT(*) AS total FROM orders", completedSql.get());
        assertEquals("SELECT COUNT(*) AS total FROM orders", result.sql());
        assertTrue(result.attempts() >= 1);
        assertTrue(result.tokenUsage().totalTokens() > 0);
    }

    /**
     * 用可预测的假 TokenStream 模拟模型逐 token 输出，避免测试依赖真实大模型。
     */
    private static TokenStream fixedTokenStream(String response) {
        return new TokenStream() {
            private java.util.function.Consumer<String> partialResponseConsumer = token -> {
            };
            private java.util.function.Consumer<List<dev.langchain4j.rag.content.Content>> retrievedConsumer = contents -> {
            };
            private java.util.function.Consumer<dev.langchain4j.service.tool.ToolExecution> toolExecutedConsumer = execution -> {
            };
            private java.util.function.Consumer<ChatResponse> completeResponseConsumer = chatResponse -> {
            };
            private java.util.function.Consumer<Throwable> errorConsumer = error -> {
            };

            @Override
            public TokenStream onPartialResponse(java.util.function.Consumer<String> partialResponseConsumer) {
                this.partialResponseConsumer = partialResponseConsumer;
                return this;
            }

            @Override
            public TokenStream onRetrieved(java.util.function.Consumer<List<dev.langchain4j.rag.content.Content>> retrievedConsumer) {
                this.retrievedConsumer = retrievedConsumer;
                return this;
            }

            @Override
            public TokenStream onToolExecuted(java.util.function.Consumer<dev.langchain4j.service.tool.ToolExecution> toolExecutedConsumer) {
                this.toolExecutedConsumer = toolExecutedConsumer;
                return this;
            }

            @Override
            public TokenStream onCompleteResponse(java.util.function.Consumer<ChatResponse> completeResponseConsumer) {
                this.completeResponseConsumer = completeResponseConsumer;
                return this;
            }

            @Override
            public TokenStream onError(java.util.function.Consumer<Throwable> errorConsumer) {
                this.errorConsumer = errorConsumer;
                return this;
            }

            @Override
            public TokenStream ignoreErrors() {
                this.errorConsumer = error -> {
                };
                return this;
            }

            @Override
            public void start() {
                try {
                    retrievedConsumer.accept(List.of());
                    for (char token : response.toCharArray()) {
                        partialResponseConsumer.accept(String.valueOf(token));
                    }
                    completeResponseConsumer.accept(ChatResponse.builder()
                            .aiMessage(new AiMessage(response))
                            .tokenUsage(new TokenUsage(response.length(), response.length() / 2, response.length() + response.length() / 2))
                            .build());
                } catch (Exception ex) {
                    errorConsumer.accept(ex);
                }
            }
        };
    }
}

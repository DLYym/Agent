package com.yk.demoai.service.impl;

import com.yk.demoai.model.ColumnSchema;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaSnapshot;
import com.yk.demoai.model.TableSchema;
import com.yk.demoai.service.DatabaseSchemaExtractor;
import com.yk.demoai.service.DynamicDataSourceManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从 JDBC 元数据和 MySQL 的 information_schema 中抽取结构化 Schema 信息。
 */
@Service
@RequiredArgsConstructor
public class DatabaseSchemaExtractorImpl implements DatabaseSchemaExtractor {

    private final DynamicDataSourceManager dataSourceManager;

    @Override
    public SchemaSnapshot extract(DatasourceDescriptor descriptor) {
        DataSource dataSource = dataSourceManager.getDataSource(descriptor);
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            DatabaseMetaData metaData = connection.getMetaData();

            String catalog = StringUtils.hasText(descriptor.catalog()) ? descriptor.catalog() : connection.getCatalog();
            String schemaName = StringUtils.hasText(descriptor.schemaName()) ? descriptor.schemaName() : resolveSchema(connection);
            String databaseProductName = metaData.getDatabaseProductName();

            // 根据数据库类型加载注释信息
            Map<String, String> tableComments = Map.of();
            Map<String, Map<String, String>> columnComments = Map.of();
            
            if (isMySql(databaseProductName)) {
                tableComments = loadMySqlTableComments(connection, catalog, descriptor.tableNames());
                columnComments = loadMySqlColumnComments(connection, catalog, descriptor.tableNames());
            } else if (isOracle(databaseProductName)) {
                tableComments = loadOracleTableComments(connection, descriptor.tableNames());
                columnComments = loadOracleColumnComments(connection, descriptor.tableNames());
            }

            List<TableSchema> tables = new ArrayList<>();
            Set<String> requestedTables = normalizeTableFilter(descriptor.tableNames());

            try (ResultSet tableResultSet = metaData.getTables(catalog, schemaName, "%", new String[]{"TABLE"})) {
                while (tableResultSet.next()) {
                    String tableName = tableResultSet.getString("TABLE_NAME");
                    if (!requestedTables.isEmpty() && !requestedTables.contains(tableName.toLowerCase(Locale.ROOT))) {
                        continue;
                    }

                    String tableComment = firstNonBlank(
                            tableResultSet.getString("REMARKS"),
                            tableComments.get(tableName)
                    );

                    tables.add(new TableSchema(
                            descriptor.id(),
                            descriptor.displayName(),
                            catalog,
                            schemaName,
                            tableName,
                            tableComment,
                            extractColumns(metaData, catalog, schemaName, tableName, columnComments.getOrDefault(tableName, Map.of()))
                    ));
                }
            }

            return new SchemaSnapshot(descriptor, databaseProductName, tables);
        } catch (SQLException ex) {
            throw new IllegalStateException("读取数据库 Schema 失败: " + ex.getMessage(), ex);
        }
    }

    private List<ColumnSchema> extractColumns(DatabaseMetaData metaData,
                                              String catalog,
                                              String schemaName,
                                              String tableName,
                                              Map<String, String> fallbackComments) throws SQLException {
        List<ColumnSchema> columns = new ArrayList<>();
        Set<String> primaryKeys = loadPrimaryKeys(metaData, catalog, schemaName, tableName);

        try (ResultSet columnsResultSet = metaData.getColumns(catalog, schemaName, tableName, "%")) {
            while (columnsResultSet.next()) {
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                String comment = firstNonBlank(
                        columnsResultSet.getString("REMARKS"),
                        fallbackComments.get(columnName)
                );

                columns.add(new ColumnSchema(
                        columnName,
                        columnsResultSet.getString("TYPE_NAME"),
                        comment,
                        columnsResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        primaryKeys.contains(columnName)
                ));
            }
        }
        return columns;
    }

    private Set<String> loadPrimaryKeys(DatabaseMetaData metaData,
                                        String catalog,
                                        String schemaName,
                                        String tableName) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet primaryKeyResultSet = metaData.getPrimaryKeys(catalog, schemaName, tableName)) {
            while (primaryKeyResultSet.next()) {
                primaryKeys.add(primaryKeyResultSet.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    private Map<String, String> loadMySqlTableComments(Connection connection,
                                                       String catalog,
                                                       Collection<String> tableNames) throws SQLException {
        if (!StringUtils.hasText(catalog)) {
            return Map.of();
        }

        String sql = buildInClauseSql(
                "SELECT table_name, table_comment " +
                "FROM information_schema.tables " +
                "WHERE table_schema = ? ",
                tableNames, "table_name");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCatalogAndTableNames(statement, catalog, tableNames);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, String> result = new HashMap<>();
                while (resultSet.next()) {
                    result.put(resultSet.getString("table_name"), resultSet.getString("table_comment"));
                }
                return result;
            }
        }
    }

    private Map<String, Map<String, String>> loadMySqlColumnComments(Connection connection,
                                                                     String catalog,
                                                                     Collection<String> tableNames) throws SQLException {
        if (!StringUtils.hasText(catalog)) {
            return Map.of();
        }

        String sql = buildInClauseSql(
                "SELECT table_name, column_name, column_comment " +
                "FROM information_schema.columns " +
                "WHERE table_schema = ? ",
                tableNames, "table_name");

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindCatalogAndTableNames(statement, catalog, tableNames);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, Map<String, String>> result = new LinkedHashMap<>();
                while (resultSet.next()) {
                    result.computeIfAbsent(resultSet.getString("table_name"), key -> new HashMap<>())
                            .put(resultSet.getString("column_name"), resultSet.getString("column_comment"));
                }
                return result;
            }
        }
    }
    
    /**
     * 从 Oracle 系统视图加载表注释
     */
    private Map<String, String> loadOracleTableComments(Connection connection,
                                                       Collection<String> tableNames) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT table_name, comments " +
                "FROM user_tab_comments " +
                "WHERE comments IS NOT NULL ");
        
        if (tableNames != null && !tableNames.isEmpty()) {
            sql.append(" AND table_name IN (");
            sql.append(String.join(",", tableNames.stream().map(name -> "'" + name.toUpperCase() + "'").toList()));
            sql.append(")");
        }
        
        try (PreparedStatement statement = connection.prepareStatement(sql.toString());
             ResultSet resultSet = statement.executeQuery()) {
            Map<String, String> result = new HashMap<>();
            while (resultSet.next()) {
                result.put(resultSet.getString("table_name").toLowerCase(), resultSet.getString("comments"));
            }
            return result;
        }
    }
    
    /**
     * 从 Oracle 系统视图加载列注释
     */
    private Map<String, Map<String, String>> loadOracleColumnComments(Connection connection,
                                                                     Collection<String> tableNames) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT table_name, column_name, comments " +
                "FROM user_col_comments " +
                "WHERE comments IS NOT NULL ");
        
        if (tableNames != null && !tableNames.isEmpty()) {
            sql.append(" AND table_name IN (");
            sql.append(String.join(",", tableNames.stream().map(name -> "'" + name.toUpperCase() + "'").toList()));
            sql.append(")");
        }
        
        try (PreparedStatement statement = connection.prepareStatement(sql.toString());
             ResultSet resultSet = statement.executeQuery()) {
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            while (resultSet.next()) {
                String tableName = resultSet.getString("table_name").toLowerCase();
                String columnName = resultSet.getString("column_name").toLowerCase();
                String comment = resultSet.getString("comments");
                
                result.computeIfAbsent(tableName, key -> new HashMap<>())
                        .put(columnName, comment);
            }
            return result;
        }
    }

    private void bindCatalogAndTableNames(PreparedStatement statement,
                                          String catalog,
                                          Collection<String> tableNames) throws SQLException {
        statement.setString(1, catalog);
        int index = 2;
        for (String tableName : tableNames) {
            statement.setString(index++, tableName);
        }
    }

    private String buildInClauseSql(String baseSql, Collection<String> tableNames, String fieldName) {
        if (tableNames == null || tableNames.isEmpty()) {
            return baseSql;
        }
        String placeholders = String.join(",", tableNames.stream().map(table -> "?").toList());
        return baseSql + " AND " + fieldName + " IN (" + placeholders + ")";
    }

    private Set<String> normalizeTableFilter(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Set.of();
        }
        return tableNames.stream()
                .map(tableName -> tableName.toLowerCase(Locale.ROOT))
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
    }

    private boolean isMySql(String databaseProductName) {
        return StringUtils.hasText(databaseProductName)
                && databaseProductName.toLowerCase(Locale.ROOT).contains("mysql");
    }
    
    private boolean isOracle(String databaseProductName) {
        return StringUtils.hasText(databaseProductName)
                && (databaseProductName.toLowerCase(Locale.ROOT).contains("oracle") 
                    || databaseProductName.toLowerCase(Locale.ROOT).contains("orcl"));
    }

    private String resolveSchema(Connection connection) throws SQLException {
        try {
            return connection.getSchema();
        } catch (AbstractMethodError | UnsupportedOperationException e) {
            // Fallback for old JDBC drivers that don't implement getSchema()
            return connection.getMetaData().getUserName();
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return "";
    }
}

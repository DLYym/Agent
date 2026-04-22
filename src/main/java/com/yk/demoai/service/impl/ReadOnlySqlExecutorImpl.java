package com.yk.demoai.service.impl;

import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.service.DatabaseTypeAdapter;
import com.yk.demoai.service.DynamicDataSourceManager;
import com.yk.demoai.service.ReadOnlySqlExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 提供只读 SQL 的执行能力，并在执行前做基础安全校验。
 * 支持不同数据库类型的SQL语法适配。
 */
@Service
@RequiredArgsConstructor
public class ReadOnlySqlExecutorImpl implements ReadOnlySqlExecutor {

    private static final Pattern READ_ONLY_PATTERN = Pattern.compile(
            "^\\s*(SELECT|SHOW|DESC|DESCRIBE|EXPLAIN|WITH)\\s+.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final DynamicDataSourceManager dataSourceManager;
    private final DatabaseTypeAdapter databaseTypeAdapter;

    @Override
    public List<Map<String, Object>> execute(DatasourceDescriptor datasource, String sql) {
        if (!isReadOnly(sql)) {
            throw new IllegalArgumentException("安全拦截：本工具仅支持查询操作，禁止增删改。");
        }

        // 识别数据库类型
        DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
        
        // 验证SQL语法是否符合数据库类型要求
        if (!databaseTypeAdapter.isValidSyntax(sql, dbType)) {
            throw new IllegalArgumentException(
                String.format("SQL语法不适用于%s数据库: %s", 
                             databaseTypeAdapter.getFriendlyTypeName(dbType), 
                             sql));
        }

        DataSource dataSource = dataSourceManager.getDataSource(datasource);
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setReadOnly(true);
            // 限制单次查询返回量，避免大模型生成全表扫描时拖垮接口。
            statement.setMaxRows(1000);

            try (ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                        row.put(metaData.getColumnLabel(columnIndex), normalizeColumnValue(resultSet, columnIndex));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("SQL 执行失败: " + ex.getMessage(), ex);
        }

        return rows;
    }

    @Override
    public boolean isReadOnly(String sql) {
        return sql != null && READ_ONLY_PATTERN.matcher(sql.trim()).matches();
    }

    /**
     * 将 ResultSet 中的列值转换为 JSON 可序列化的标准 Java 类型。
     * Oracle JDBC 驱动的 getObject() 会返回 oracle.sql.TIMESTAMP 等私有类型，
     * 这些类型内部包含 ByteArrayInputStream 等字段，Jackson 无法处理。
     */
    private Object normalizeColumnValue(ResultSet resultSet, int columnIndex) throws SQLException {
        Object value = resultSet.getObject(columnIndex);
        if (value == null) {
            return null;
        }
        // 标准 JSON 安全类型：直接返回
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        // 标准 JDBC 时间类型（java.sql.Timestamp / Date / Time 都是 java.util.Date 子类）
        if (value instanceof java.util.Date) {
            return value.toString();
        }
        // CLOB -> 截取文本
        if (value instanceof java.sql.Clob clob) {
            long length = clob.length();
            return clob.getSubString(1, (int) Math.min(length, 4000));
        }
        // BLOB -> 占位符
        if (value instanceof java.sql.Blob) {
            return "[BLOB]";
        }
        // byte[] -> 占位符
        if (value instanceof byte[]) {
            return "[BINARY " + ((byte[]) value).length + " bytes]";
        }
        // 厂商私有类型（oracle.sql.TIMESTAMP、oracle.sql.DATE 等）：
        // 用 getString() 让 JDBC 驱动自行转换为字符串
        String str = resultSet.getString(columnIndex);
        return str != null ? str : value.toString();
    }

    /**
     * 执行带限制的表数据查询
     */
    public List<Map<String, Object>> executeTableSample(DatasourceDescriptor datasource, String tableName, int limit) {
        DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
        String sql = databaseTypeAdapter.generateLimitedSelect(tableName, limit, dbType);
        return execute(datasource, sql);
    }

    /**
     * 获取表结构信息
     */
    public List<Map<String, Object>> describeTable(DatasourceDescriptor datasource, String tableName) {
        DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
        String sql = databaseTypeAdapter.generateDescribeTable(tableName, dbType);
        return execute(datasource, sql);
    }

    /**
     * 获取表记录数
     */
    public List<Map<String, Object>> getTableCount(DatasourceDescriptor datasource, String tableName) {
        DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
        String sql = databaseTypeAdapter.generateCountQuery(tableName, dbType);
        return execute(datasource, sql);
    }

    /**
     * 执行分页查询
     */
    public List<Map<String, Object>> executePaginatedQuery(DatasourceDescriptor datasource, 
                                                           String tableName, 
                                                           int offset, 
                                                           int limit) {
        DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
        String sql = databaseTypeAdapter.generatePaginatedQuery(tableName, offset, limit, dbType);
        return execute(datasource, sql);
    }

    /**
     * 获取数据库中的表列表
     */
    public List<Map<String, Object>> listTables(DatasourceDescriptor datasource) {
        DatabaseTypeAdapter.DatabaseType dbType = databaseTypeAdapter.identifyDatabaseType(datasource);
        String sql = databaseTypeAdapter.generateTableListQuery(dbType);
        return execute(datasource, sql);
    }
}

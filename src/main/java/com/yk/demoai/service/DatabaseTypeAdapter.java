package com.yk.demoai.service;

import com.yk.demoai.model.DatasourceDescriptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 数据库类型识别和SQL适配服务
 * 根据数据库类型提供相应的SQL语法和功能支持
 */
@Service
public class DatabaseTypeAdapter {

    public enum DatabaseType {
        MYSQL, ORACLE, POSTGRESQL, UNKNOWN
    }

    /**
     * 根据数据源描述识别数据库类型
     */
    public DatabaseType identifyDatabaseType(DatasourceDescriptor datasource) {
        // 首先尝试从JDBC URL识别
        String jdbcUrl = datasource.jdbcUrl();
        if (StringUtils.hasText(jdbcUrl)) {
            String urlLower = jdbcUrl.toLowerCase(Locale.ROOT);
            if (urlLower.contains(":mysql:")) {
                return DatabaseType.MYSQL;
            } else if (urlLower.contains(":oracle:")) {
                return DatabaseType.ORACLE;
            } else if (urlLower.contains(":postgresql:")) {
                return DatabaseType.POSTGRESQL;
            }
        }

        // 如果URL识别失败，尝试从驱动类名识别
        String driverClassName = datasource.driverClassName();
        if (StringUtils.hasText(driverClassName)) {
            String driverLower = driverClassName.toLowerCase(Locale.ROOT);
            if (driverLower.contains("mysql")) {
                return DatabaseType.MYSQL;
            } else if (driverLower.contains("oracle")) {
                return DatabaseType.ORACLE;
            } else if (driverLower.contains("postgresql")) {
                return DatabaseType.POSTGRESQL;
            }
        }

        return DatabaseType.UNKNOWN;
    }

    /**
     * 生成限制行数的SELECT语句
     */
    public String generateLimitedSelect(String tableName, int limit, DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
            case POSTGRESQL:
                return String.format("SELECT * FROM %s LIMIT %d", tableName, limit);
            case ORACLE:
                // Oracle 11g使用ROWNUM，12c+可以使用FETCH FIRST
                return String.format("SELECT * FROM %s WHERE ROWNUM <= %d", tableName, limit);
            default:
                return String.format("SELECT * FROM %s", tableName);
        }
    }

    /**
     * 生成表结构查询语句
     */
    public String generateDescribeTable(String tableName, DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
                return String.format("DESCRIBE %s", tableName);
            case ORACLE:
                return String.format(
                    "SELECT column_name, data_type, nullable, data_length " +
                    "FROM user_tab_columns " +
                    "WHERE table_name = UPPER('%s')", tableName);
            case POSTGRESQL:
                return String.format(
                    "SELECT column_name, data_type, is_nullable " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = '%s'", tableName);
            default:
                return String.format("SELECT * FROM %s WHERE 1=0", tableName);
        }
    }

    /**
     * 生成表计数查询语句
     */
    public String generateCountQuery(String tableName, DatabaseType dbType) {
        return String.format("SELECT COUNT(*) AS total_count FROM %s", tableName);
    }

    /**
     * 生成分页查询语句
     */
    public String generatePaginatedQuery(String tableName, int offset, int limit, DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
            case POSTGRESQL:
                return String.format("SELECT * FROM %s LIMIT %d OFFSET %d", tableName, limit, offset);
            case ORACLE:
                // Oracle 11g分页语法
                return String.format(
                    "SELECT * FROM (SELECT t.*, ROWNUM rn FROM (SELECT * FROM %s) t WHERE ROWNUM <= %d) " +
                    "WHERE rn > %d", tableName, offset + limit, offset);
            default:
                return generateLimitedSelect(tableName, limit, dbType);
        }
    }

    /**
     * 生成表列表查询语句
     */
    public String generateTableListQuery(DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
                return "SHOW TABLES";
            case ORACLE:
                return "SELECT table_name FROM user_tables ORDER BY table_name";
            case POSTGRESQL:
                return "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename";
            default:
                return "SELECT table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE'";
        }
    }

    /**
     * 生成数据库列表查询语句
     */
    public String generateDatabaseListQuery(DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
                return "SHOW DATABASES";
            case ORACLE:
                // Oracle中通常连接时已经指定了SID/Service，这里返回当前连接的服务信息
                return "SELECT SYS_CONTEXT('USERENV', 'DB_NAME') AS database_name FROM DUAL";
            case POSTGRESQL:
                return "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname";
            default:
                return null; // 对于未知数据库类型，不执行查询
        }
    }

    /**
     * 获取数据库类型对应的友好名称
     */
    public String getFriendlyTypeName(DatabaseType dbType) {
        switch (dbType) {
            case MYSQL:
                return "MySQL";
            case ORACLE:
                return "Oracle";
            case POSTGRESQL:
                return "PostgreSQL";
            default:
                return "Unknown";
        }
    }

    /**
     * 检查SQL是否符合该数据库类型的语法要求
     */
    public boolean isValidSyntax(String sql, DatabaseType dbType) {
        if (!StringUtils.hasText(sql)) {
            return false;
        }

        String sqlUpper = sql.trim().toUpperCase(Locale.ROOT);
        
        switch (dbType) {
            case ORACLE:
                // Oracle不支持LIMIT语法
                return !sqlUpper.matches(".*LIMIT\\s+\\d+.*");
            case MYSQL:
            case POSTGRESQL:
                // MySQL和PostgreSQL支持LIMIT语法
                return true;
            default:
                return true;
        }
    }

    /**
     * 转换SQL语法以适配目标数据库
     */
    public String adaptSqlSyntax(String sql, DatabaseType sourceType, DatabaseType targetType) {
        if (sourceType == targetType || !StringUtils.hasText(sql)) {
            return sql;
        }

        String adaptedSql = sql;

        // 从MySQL转换到Oracle
        if (sourceType == DatabaseType.MYSQL && targetType == DatabaseType.ORACLE) {
            // 简单的LIMIT替换为ROWNUM（基础版本）
            adaptedSql = adaptedSql.replaceAll("(?i)LIMIT\\s+(\\d+)", " WHERE ROWNUM <= $1");
        }

        // 从Oracle转换到MySQL
        else if (sourceType == DatabaseType.ORACLE && targetType == DatabaseType.MYSQL) {
            // 替换ROWNUM语法为LIMIT
            adaptedSql = adaptedSql.replaceAll(
                "(?i)WHERE\\s+ROWNUM\\s*(<=|<|>|>=|=)\\s*(\\d+)",
                "LIMIT $2"
            );
        }

        return adaptedSql;
    }
}
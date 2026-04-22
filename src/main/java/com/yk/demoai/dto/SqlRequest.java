package com.yk.demoai.dto;
import lombok.Data;

import java.util.List;

@Data
public class SqlRequest {
    private String tableSchema;

    // 用户的自然语言问题
    private String question;

    private String sql;

    private String dbUrl;

    private String username;

    private String password;

    private String driverClassName;

    private String catalog;

    private String schemaName;

    private String targetDatasourceId;

    // 可选：指定只想查哪几张表，如果为空则查全库
    private String[] tableNames;

    // 新增：支持一次请求挂载多个数据源做库级路由
    private List<DatasourceRequest> datasources;
}

package com.yk.demoai.dto;

import lombok.Data;

@Data
public class DatasourceRequest {
    private String id;

    private String name;

    private String dbUrl;

    private String username;

    private String password;

    private String driverClassName;

    private String catalog;

    private String schemaName;

    private String[] tableNames;
}

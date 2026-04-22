package com.yk.demoai.model;

import java.util.List;

public record SchemaSnapshot(
        DatasourceDescriptor datasource,
        String databaseProductName,
        List<TableSchema> tables
) {
}

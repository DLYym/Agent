package com.yk.demoai.model;

import java.util.List;

public record TableSchema(
        String datasourceId,
        String datasourceName,
        String catalog,
        String schemaName,
        String tableName,
        String tableComment,
        List<ColumnSchema> columns
) {
}

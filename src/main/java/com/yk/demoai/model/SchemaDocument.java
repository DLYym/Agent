package com.yk.demoai.model;

import java.util.List;

public record SchemaDocument(
        String id,
        String datasourceId,
        String datasourceName,
        String catalog,
        String schemaName,
        String tableName,
        String tableComment,
        List<String> keywords,
        String content
) {
}

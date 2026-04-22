package com.yk.demoai.model;

import java.util.List;

public record SchemaRetrievalContext(
        DatasourceDescriptor targetDatasource,
        String databaseType,
        String schemaContext,
        List<SchemaSearchHit> hits
) {
}

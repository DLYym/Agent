package com.yk.demoai.model;

import java.util.List;

public record SchemaRetrievalContext(
        DatasourceDescriptor targetDatasource,
        String databaseType,
        String schemaContext,
        List<SchemaSearchHit> hits,
        List<String> logicalRelations
) {
    public String logicalRelationsContext() {
        if (logicalRelations == null || logicalRelations.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### 已知表关联关系 (Logical Relations)\n");
        sb.append("以下是表之间的逻辑外键关系，可以帮助你理解表之间的关联方式：\n");
        for (String relation : logicalRelations) {
            sb.append("- ").append(relation).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}

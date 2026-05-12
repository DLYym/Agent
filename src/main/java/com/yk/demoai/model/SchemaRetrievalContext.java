package com.yk.demoai.model;

import java.util.List;
import java.util.stream.Collectors;

public record SchemaRetrievalContext(
        DatasourceDescriptor targetDatasource,
        String databaseType,
        String schemaContext,
        List<SchemaSearchHit> hits,
        List<String> logicalRelations,
        List<String> dictMappings
) {
    public String logicalRelationsContext() {
        StringBuilder sb = new StringBuilder();

        if (logicalRelations != null && !logicalRelations.isEmpty()) {
            sb.append("### 已知表关联关系 (Logical Relations)\n");
            sb.append("以下是表之间的逻辑外键关系，查询时请优先用关联表的可读字段（如姓名）代替 ID 字段：\n");
            for (String relation : logicalRelations) {
                sb.append("- ").append(relation).append("\n");
            }
            sb.append("\n");
        }

        if (dictMappings != null && !dictMappings.isEmpty()) {
            sb.append("### 已知字典映射 (Dictionary Mappings)\n");
            sb.append("以下字段的值需要通过 JOIN 字典表转换为可读的中文名称，查询时请务必 JOIN 字典表并使用 dict_name 字段：\n");
            for (String mapping : dictMappings) {
                sb.append("- ").append(mapping).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String foreignKeyReplacementRules() {
        if (logicalRelations == null || logicalRelations.isEmpty()) {
            return "（无外键关联规则）";
        }

        StringBuilder sb = new StringBuilder();
        for (String relation : logicalRelations) {
            if (relation.contains(" = ")) {
                String[] parts = relation.split(" = ");
                if (parts.length == 2) {
                    String leftPart = parts[0].trim();
                    String rightPart = parts[1].trim();

                    if (leftPart.contains(".") && rightPart.contains(".")) {
                        String leftTable = leftPart.substring(0, leftPart.indexOf("."));
                        String leftField = leftPart.substring(leftPart.indexOf(".") + 1);
                        String rightTable = rightPart.substring(0, rightPart.indexOf("."));
                        String rightField = rightPart.substring(rightPart.indexOf(".") + 1);

                        sb.append(String.format("- 当查询涉及 %s.%s 时，必须 JOIN %s 表，并用 %s.%s 替代 %s.%s\n",
                                leftTable, leftField,
                                rightTable,
                                rightTable, rightField,
                                leftTable, leftField));
                    }
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : "（无需要替换的外键字段）";
    }

    public String dictMappingReplacementRules() {
        if (dictMappings == null || dictMappings.isEmpty()) {
            return "（无字典映射规则）";
        }

        StringBuilder sb = new StringBuilder();
        for (String mapping : dictMappings) {
            if (mapping.contains(" → ")) {
                String[] parts = mapping.split(" → ");
                if (parts.length >= 2) {
                    String sourcePart = parts[0].trim();
                    String targetPart = parts[1].trim();
                    String condition = parts.length == 3 ? parts[2].trim() : "";

                    if (sourcePart.contains(".") && targetPart.contains(".")) {
                        String sourceTable = sourcePart.substring(0, sourcePart.indexOf("."));
                        String sourceField = sourcePart.substring(sourcePart.indexOf(".") + 1);
                        String targetTable = targetPart.contains(".")
                                ? targetPart.substring(0, targetPart.indexOf("."))
                                : targetPart.split("\\(")[0].trim();
                        String targetField = targetPart.contains(".")
                                ? targetPart.substring(targetPart.indexOf(".") + 1).split("\\(")[0].trim()
                                : "dict_code";

                        String joinCondition = "";
                        if (!condition.isEmpty()) {
                            condition = condition.replace("(", "").replace(")", "");
                            joinCondition = String.format(" AND %s", condition);
                        }
                        String dictTypeValue = "";
                        if (condition.contains("=")) {
                            String[] condParts = condition.split("=");
                            if (condParts.length == 2) {
                                dictTypeValue = condParts[1].trim();
                            }
                        }

                        sb.append(String.format("- 当查询涉及 %s.%s 时，必须 JOIN %s 表，并用 %s.dict_name 替代 %s.%s\n",
                                sourceTable, sourceField,
                                targetTable,
                                targetTable,
                                sourceTable, sourceField));

                        sb.append(String.format("  JOIN 示例：LEFT JOIN %s ON %s.%s = %s.%s%s\n",
                                targetTable,
                                sourceTable, sourceField,
                                targetTable, targetField,
                                joinCondition));
                    }
                }
            }
        }

        return sb.length() > 0 ? sb.toString() : "（无字典映射规则）";
    }
}

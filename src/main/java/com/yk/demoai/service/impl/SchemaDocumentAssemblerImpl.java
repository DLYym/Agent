package com.yk.demoai.service.impl;

import com.yk.demoai.model.*;
import com.yk.demoai.service.SchemaDocumentAssembler;
import com.yk.demoai.util.SchemaTermTokenizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将结构化的表信息压平成检索文档和提示词文本。
 */
@Service
public class SchemaDocumentAssemblerImpl implements SchemaDocumentAssembler {

    @Override
    public List<SchemaDocument> toDocuments(SchemaSnapshot snapshot) {
        return snapshot.tables().stream()
                .map(table -> toDocument(snapshot.datasource(), snapshot.databaseProductName(), table))
                .toList();
    }

    @Override
    public SchemaDocument toDocument(DatasourceDescriptor datasource, String databaseProductName, TableSchema table) {
        return new SchemaDocument(
                datasource.id() + "::" + table.tableName(),
                table.datasourceId(),
                table.datasourceName(),
                table.catalog(),
                table.schemaName(),
                table.tableName(),
                table.tableComment(),
                buildKeywords(table),
                buildContent(datasource, databaseProductName, table)
        );
    }

    @Override
    public String toPromptText(SchemaSnapshot snapshot) {
        return toDocuments(snapshot).stream()
                .map(SchemaDocument::content)
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("未检索到可用的表结构信息");
    }

    private String buildContent(DatasourceDescriptor datasource, String databaseProductName, TableSchema table) {
        StringBuilder builder = new StringBuilder();
        builder.append("数据源: ").append(table.datasourceName()).append('\n');
        builder.append("数据库类型: ").append(databaseProductName).append('\n');
        builder.append("Catalog: ").append(defaultText(table.catalog(), "default")).append('\n');
        builder.append("Schema: ").append(defaultText(table.schemaName(), "default")).append('\n');
        builder.append("表名: ").append(table.tableName()).append('\n');
        builder.append("表注释: ").append(defaultText(table.tableComment(), "无")).append('\n');
        builder.append("字段定义:\n");
        for (ColumnSchema column : table.columns()) {
            builder.append("- ").append(column.name())
                    .append(' ').append(column.type());
            if (!column.nullable()) {
                builder.append(" NOT NULL");
            }
            if (column.primaryKey()) {
                builder.append(" PRIMARY KEY");
            }
            if (StringUtils.hasText(column.comment())) {
                builder.append(" // ").append(column.comment());
            }
            builder.append('\n');
        }
        return builder.toString().trim();
    }

    private List<String> buildKeywords(TableSchema table) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(table.tableName().toLowerCase(Locale.ROOT));
        keywords.addAll(splitIdentifier(table.tableName()));
        if (StringUtils.hasText(table.tableComment())) {
            keywords.add(table.tableComment().toLowerCase(Locale.ROOT));
            keywords.addAll(SchemaTermTokenizer.extractSchemaTerms(table.tableComment()));
        }
        table.columns().stream()
                .limit(12)
                .forEach(column -> {
                    keywords.add(column.name().toLowerCase(Locale.ROOT));
                    keywords.addAll(splitIdentifier(column.name()));
                    if (StringUtils.hasText(column.comment())) {
                        keywords.add(column.comment().toLowerCase(Locale.ROOT));
                        keywords.addAll(SchemaTermTokenizer.extractSchemaTerms(column.comment()));
                    }
                });
        return new ArrayList<>(keywords);
    }

    private List<String> splitIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return List.of();
        }
        // 将 camelCase 先打散，再统一按非字母数字字符切词。
        String normalized = identifier.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(normalized.split("[^A-Za-z0-9]+"))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}

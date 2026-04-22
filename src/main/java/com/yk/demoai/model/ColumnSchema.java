package com.yk.demoai.model;

public record ColumnSchema(
        String name,
        String type,
        String comment,
        boolean nullable,
        boolean primaryKey
) {
}

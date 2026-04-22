package com.yk.demoai.model;

public record SchemaSearchHit(
        SchemaDocument document,
        double vectorScore,
        double keywordScore,
        double finalScore,
        boolean exactTableNameHit
) {
}

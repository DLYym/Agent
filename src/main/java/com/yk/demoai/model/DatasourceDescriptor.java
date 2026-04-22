package com.yk.demoai.model;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

public record DatasourceDescriptor(
        String id,
        String name,
        String jdbcUrl,
        String username,
        String password,
        String driverClassName,
        String catalog,
        String schemaName,
        List<String> tableNames
) {

    public DatasourceDescriptor {
        id = StringUtils.hasText(id) ? id.trim() : generateId(jdbcUrl, username);
        name = StringUtils.hasText(name) ? name.trim() : id;
        driverClassName = normalize(driverClassName);
        catalog = normalize(catalog);
        schemaName = normalize(schemaName);
        tableNames = tableNames == null ? List.of() : tableNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public String displayName() {
        return StringUtils.hasText(name) ? name : id;
    }

    public String cacheKey() {
        return String.join("||",
                Objects.toString(driverClassName, ""),
                Objects.toString(jdbcUrl, ""),
                Objects.toString(username, ""),
                Objects.toString(password, ""));
    }

    public boolean hasTableFilter() {
        return !tableNames.isEmpty();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String generateId(String jdbcUrl, String username) {
        return "ds-" + Integer.toHexString(Objects.hash(jdbcUrl, username));
    }
}

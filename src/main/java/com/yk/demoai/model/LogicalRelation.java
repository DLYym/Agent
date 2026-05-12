package com.yk.demoai.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogicalRelation {

    public static final String CATEGORY_FOREIGN_KEY = "FOREIGN_KEY";
    public static final String CATEGORY_DICT_MAPPING = "DICT_MAPPING";

    private Integer id;

    private String datasourceId;

    private String sourceTableName;

    private String sourceColumnName;

    private String targetTableName;

    private String targetColumnName;

    private String relationType;

    private String relationCategory;

    private String description;

    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    public String toForeignKeyFormat() {
        return String.format("%s.%s=%s.%s", 
                sourceTableName, sourceColumnName, 
                targetTableName, targetColumnName);
    }

    public boolean isDictMapping() {
        return CATEGORY_DICT_MAPPING.equals(relationCategory);
    }
}

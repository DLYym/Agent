package com.yk.demoai.service;

import com.yk.demoai.model.LogicalRelation;

import java.util.List;
import java.util.Set;

public interface LogicalRelationService {

    List<LogicalRelation> getLogicalRelations(String datasourceId);

    LogicalRelation getLogicalRelationById(Integer id);

    LogicalRelation addLogicalRelation(LogicalRelation logicalRelation);

    LogicalRelation updateLogicalRelation(Integer id, LogicalRelation logicalRelation);

    void deleteLogicalRelation(Integer id);

    void deleteLogicalRelationsByDatasourceId(String datasourceId);

    List<LogicalRelation> saveLogicalRelations(String datasourceId, List<LogicalRelation> logicalRelations);

    List<String> getFormattedForeignKeys(String datasourceId, Set<String> tableNames);
}

package com.yk.demoai.service.impl;

import com.yk.demoai.mapper.LogicalRelationMapper;
import com.yk.demoai.model.LogicalRelation;
import com.yk.demoai.service.LogicalRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogicalRelationServiceImpl implements LogicalRelationService {

    private final LogicalRelationMapper logicalRelationMapper;

    @Override
    public List<LogicalRelation> getLogicalRelations(String datasourceId) {
        log.info("Getting logical relations for datasource: {}", datasourceId);
        return logicalRelationMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public LogicalRelation getLogicalRelationById(Integer id) {
        return logicalRelationMapper.selectById(id);
    }

    @Override
    public LogicalRelation addLogicalRelation(LogicalRelation logicalRelation) {
        log.info("Adding logical relation for datasource: {}", logicalRelation.getDatasourceId());

        int exists = logicalRelationMapper.checkExists(
                logicalRelation.getDatasourceId(),
                logicalRelation.getSourceTableName(),
                logicalRelation.getSourceColumnName(),
                logicalRelation.getTargetTableName(),
                logicalRelation.getTargetColumnName()
        );

        if (exists > 0) {
            throw new RuntimeException("该逻辑外键关系已存在");
        }

        logicalRelationMapper.insert(logicalRelation);
        log.info("Logical relation added successfully with id: {}", logicalRelation.getId());

        return logicalRelation;
    }

    @Override
    public LogicalRelation updateLogicalRelation(Integer id, LogicalRelation logicalRelation) {
        log.info("Updating logical relation: {}", id);

        LogicalRelation existingRelation = logicalRelationMapper.selectById(id);
        if (existingRelation == null) {
            throw new RuntimeException("逻辑外键不存在，ID: " + id);
        }

        logicalRelation.setId(id);
        logicalRelation.setDatasourceId(existingRelation.getDatasourceId());

        int updated = logicalRelationMapper.updateById(logicalRelation);
        if (updated == 0) {
            throw new RuntimeException("更新逻辑外键失败");
        }

        log.info("Logical relation updated successfully: {}", id);

        return logicalRelationMapper.selectById(id);
    }

    @Override
    public void deleteLogicalRelation(Integer id) {
        log.info("Deleting logical relation: {}", id);

        LogicalRelation logicalRelation = logicalRelationMapper.selectById(id);
        if (logicalRelation == null) {
            throw new RuntimeException("逻辑外键不存在，ID: " + id);
        }

        int deleted = logicalRelationMapper.deleteById(id);
        if (deleted == 0) {
            throw new RuntimeException("删除逻辑外键失败");
        }

        log.info("Logical relation deleted successfully: {}", id);
    }

    @Override
    public void deleteLogicalRelationsByDatasourceId(String datasourceId) {
        log.info("Deleting all logical relations for datasource: {}", datasourceId);
        logicalRelationMapper.deleteByDatasourceId(datasourceId);
    }

    @Override
    @Transactional
    public List<LogicalRelation> saveLogicalRelations(String datasourceId, List<LogicalRelation> logicalRelations) {
        log.info("Saving {} logical relations for datasource: {}", logicalRelations.size(), datasourceId);

        List<LogicalRelation> existingRelations = logicalRelationMapper.selectByDatasourceId(datasourceId);
        Map<Integer, LogicalRelation> existingMap = existingRelations.stream()
                .collect(Collectors.toMap(LogicalRelation::getId, relation -> relation));

        Set<Integer> incomingIds = logicalRelations.stream()
                .map(LogicalRelation::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int deletedCount = 0;
        for (LogicalRelation existing : existingRelations) {
            if (!incomingIds.contains(existing.getId())) {
                logicalRelationMapper.deleteById(existing.getId());
                deletedCount++;
                log.info("Deleted logical relation: {} -> {}", 
                        existing.getSourceTableName(), existing.getTargetTableName());
            }
        }
        log.info("Deleted {} logical relations for datasource: {}", deletedCount, datasourceId);

        List<LogicalRelation> uniqueRelations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (LogicalRelation logicalRelation : logicalRelations) {
            String key = logicalRelation.getSourceTableName() + "|" + 
                    logicalRelation.getSourceColumnName() + "|" +
                    logicalRelation.getTargetTableName() + "|" + 
                    logicalRelation.getTargetColumnName();

            if (!seen.contains(key)) {
                seen.add(key);
                uniqueRelations.add(logicalRelation);
            } else {
                log.warn("跳过重复的逻辑外键: {} -> {}", 
                        logicalRelation.getSourceTableName(), logicalRelation.getTargetTableName());
            }
        }

        int duplicateCount = logicalRelations.size() - uniqueRelations.size();
        if (duplicateCount > 0) {
            log.warn("检测到并去重了 {} 条重复的逻辑外键", duplicateCount);
        }

        int insertedCount = 0;
        int updatedCount = 0;
        for (LogicalRelation logicalRelation : uniqueRelations) {
            logicalRelation.setDatasourceId(datasourceId);

            if (logicalRelation.getId() != null && existingMap.containsKey(logicalRelation.getId())) {
                logicalRelationMapper.updateById(logicalRelation);
                updatedCount++;
                log.debug("Updated logical relation: {} -> {}", 
                        logicalRelation.getSourceTableName(), logicalRelation.getTargetTableName());
            } else {
                logicalRelation.setId(null);
                logicalRelationMapper.insert(logicalRelation);
                insertedCount++;
                log.debug("Inserted logical relation: {} -> {}", 
                        logicalRelation.getSourceTableName(), logicalRelation.getTargetTableName());
            }
        }

        log.info("Saved logical relations for datasource {}: {} inserted, {} updated, {} deleted", 
                datasourceId, insertedCount, updatedCount, deletedCount);

        return logicalRelationMapper.selectByDatasourceId(datasourceId);
    }

    @Override
    public List<String> getFormattedForeignKeys(String datasourceId, Set<String> tableNames) {
        log.info("Getting formatted foreign keys for datasource: {}, tables: {}", datasourceId, tableNames);
        
        List<LogicalRelation> allLogicalRelations = logicalRelationMapper.selectByDatasourceId(datasourceId);
        log.info("Found {} logical relations in datasource: {}", allLogicalRelations.size(), datasourceId);

        List<String> formattedForeignKeys = allLogicalRelations.stream()
                .filter(lr -> tableNames.contains(lr.getSourceTableName()) || 
                             tableNames.contains(lr.getTargetTableName()))
                .map(LogicalRelation::toForeignKeyFormat)
                .distinct()
                .collect(Collectors.toList());

        log.info("Filtered {} relevant logical relations for tables: {}", 
                formattedForeignKeys.size(), tableNames);
        return formattedForeignKeys;
    }
}

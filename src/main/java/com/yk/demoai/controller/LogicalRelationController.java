package com.yk.demoai.controller;

import com.yk.demoai.dto.Result;
import com.yk.demoai.model.LogicalRelation;
import com.yk.demoai.service.LogicalRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/logical-relations")
@RequiredArgsConstructor
public class LogicalRelationController {

    private final LogicalRelationService logicalRelationService;

    @GetMapping("/{datasourceId}")
    public Result<List<LogicalRelation>> getLogicalRelations(@PathVariable String datasourceId) {
        log.info("Getting logical relations for datasource: {}", datasourceId);
        List<LogicalRelation> relations = logicalRelationService.getLogicalRelations(datasourceId);
        return Result.success(relations);
    }

    @GetMapping("/detail/{id}")
    public Result<LogicalRelation> getLogicalRelationById(@PathVariable Integer id) {
        log.info("Getting logical relation by id: {}", id);
        LogicalRelation relation = logicalRelationService.getLogicalRelationById(id);
        if (relation == null) {
            return Result.fail("逻辑外键不存在");
        }
        return Result.success(relation);
    }

    @PostMapping
    public Result<LogicalRelation> addLogicalRelation(@RequestBody LogicalRelation logicalRelation) {
        log.info("Adding logical relation: {}", logicalRelation);
        try {
            LogicalRelation saved = logicalRelationService.addLogicalRelation(logicalRelation);
            return Result.success(saved);
        } catch (RuntimeException e) {
            log.error("Failed to add logical relation", e);
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<LogicalRelation> updateLogicalRelation(
            @PathVariable Integer id,
            @RequestBody LogicalRelation logicalRelation) {
        log.info("Updating logical relation {}: {}", id, logicalRelation);
        try {
            LogicalRelation updated = logicalRelationService.updateLogicalRelation(id, logicalRelation);
            return Result.success(updated);
        } catch (RuntimeException e) {
            log.error("Failed to update logical relation", e);
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteLogicalRelation(@PathVariable Integer id) {
        log.info("Deleting logical relation: {}", id);
        try {
            logicalRelationService.deleteLogicalRelation(id);
            return Result.success(null);
        } catch (RuntimeException e) {
            log.error("Failed to delete logical relation", e);
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/datasource/{datasourceId}")
    public Result<Void> deleteLogicalRelationsByDatasourceId(@PathVariable String datasourceId) {
        log.info("Deleting all logical relations for datasource: {}", datasourceId);
        logicalRelationService.deleteLogicalRelationsByDatasourceId(datasourceId);
        return Result.success(null);
    }

    @PostMapping("/batch/{datasourceId}")
    public Result<List<LogicalRelation>> saveLogicalRelations(
            @PathVariable String datasourceId,
            @RequestBody List<LogicalRelation> logicalRelations) {
        log.info("Batch saving {} logical relations for datasource: {}", 
                logicalRelations.size(), datasourceId);
        try {
            List<LogicalRelation> saved = logicalRelationService.saveLogicalRelations(
                    datasourceId, logicalRelations);
            return Result.success(saved);
        } catch (RuntimeException e) {
            log.error("Failed to batch save logical relations", e);
            return Result.fail(e.getMessage());
        }
    }
}

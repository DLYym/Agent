package com.yk.demoai.service.impl;

import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaDocument;
import com.yk.demoai.model.SchemaRetrievalContext;
import com.yk.demoai.model.SchemaSearchHit;
import com.yk.demoai.model.SchemaSnapshot;
import com.yk.demoai.service.DatabaseSchemaExtractor;
import com.yk.demoai.service.SchemaDocumentAssembler;
import com.yk.demoai.service.SchemaHybridRetriever;
import com.yk.demoai.service.SchemaIndexStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 负责将 Schema 抽取、索引写入、混合召回和目标数据源选择串成完整流程。
 */
@Service
@RequiredArgsConstructor
public class SchemaHybridRetrieverImpl implements SchemaHybridRetriever {

    private final DatabaseSchemaExtractor schemaExtractor;
    private final SchemaDocumentAssembler schemaDocumentAssembler;
    private final SchemaIndexStore schemaIndexStore;
    private final AppProperties properties;

    @Override
    public SchemaRetrievalContext retrieve(String question,
                                           List<DatasourceDescriptor> datasources,
                                           String targetDatasourceId) {
        if (datasources == null || datasources.isEmpty()) {
            throw new IllegalArgumentException("至少需要提供一个数据源");
        }

        Map<String, SchemaSnapshot> snapshots = new LinkedHashMap<>();
        for (DatasourceDescriptor datasource : datasources) {
            SchemaSnapshot snapshot = schemaExtractor.extract(datasource);
            snapshots.put(datasource.id(), snapshot);
            schemaIndexStore.replaceDatasourceDocuments(datasource, schemaDocumentAssembler.toDocuments(snapshot));
        }

        List<String> searchableDatasourceIds = StringUtils.hasText(targetDatasourceId)
                ? List.of(targetDatasourceId)
                : datasources.stream().map(DatasourceDescriptor::id).toList();

        List<SchemaSearchHit> mergedHits = mergeHits(
                schemaIndexStore.vectorSearch(question, searchableDatasourceIds, properties.getRag().getSchemaTopK()),
                schemaIndexStore.keywordSearch(question, searchableDatasourceIds, properties.getRag().getSchemaTopK()),
                properties.getRag().getSchemaTopK()
        );

        DatasourceDescriptor targetDatasource = selectTargetDatasource(datasources, targetDatasourceId, mergedHits);
        SchemaSnapshot targetSnapshot = snapshots.get(targetDatasource.id());
        List<SchemaSearchHit> datasourceHits = focusDatasourceHits(mergedHits.stream()
                .filter(hit -> Objects.equals(hit.document().datasourceId(), targetDatasource.id()))
                .toList());

        List<SchemaDocument> selectedDocuments = datasourceHits.stream()
                .map(SchemaSearchHit::document)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(SchemaDocument::id, document -> document, (left, right) -> left, LinkedHashMap::new),
                        result -> new ArrayList<>(result.values())
                ));

        String schemaContext = selectedDocuments.isEmpty()
                ? schemaDocumentAssembler.toPromptText(targetSnapshot)
                : selectedDocuments.stream()
                .map(SchemaDocument::content)
                .collect(Collectors.joining("\n\n---\n\n"));

        return new SchemaRetrievalContext(
                targetDatasource,
                targetSnapshot.databaseProductName(),
                schemaContext,
                datasourceHits
        );
    }

    private DatasourceDescriptor selectTargetDatasource(List<DatasourceDescriptor> datasources,
                                                        String targetDatasourceId,
                                                        List<SchemaSearchHit> mergedHits) {
        if (StringUtils.hasText(targetDatasourceId)) {
            return datasources.stream()
                    .filter(datasource -> datasource.id().equals(targetDatasourceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未找到目标数据源: " + targetDatasourceId));
        }
        if (datasources.size() == 1) {
            return datasources.getFirst();
        }
        if (mergedHits.isEmpty()) {
            return datasources.getFirst();
        }

        Map<String, Double> datasourceScores = new LinkedHashMap<>();
        for (SchemaSearchHit hit : mergedHits) {
            datasourceScores.merge(hit.document().datasourceId(), hit.finalScore(), Double::sum);
        }

        return datasources.stream()
                .max(Comparator.comparingDouble(datasource -> datasourceScores.getOrDefault(datasource.id(), 0.0)))
                .orElse(datasources.getFirst());
    }

    private List<SchemaSearchHit> mergeHits(List<SchemaSearchHit> vectorHits,
                                            List<SchemaSearchHit> keywordHits,
                                            int limit) {
        Map<String, MutableHit> merged = new LinkedHashMap<>();
        double maxVectorScore = vectorHits.stream().mapToDouble(SchemaSearchHit::vectorScore).max().orElse(1.0);
        double maxKeywordScore = keywordHits.stream().mapToDouble(SchemaSearchHit::keywordScore).max().orElse(1.0);

        for (SchemaSearchHit vectorHit : vectorHits) {
            MutableHit hit = merged.computeIfAbsent(vectorHit.document().id(), key -> new MutableHit(vectorHit.document()));
            hit.vectorScore = vectorHit.vectorScore();
            hit.exactTableNameHit = hit.exactTableNameHit || vectorHit.exactTableNameHit();
            hit.finalScore += normalizedScore(vectorHit.vectorScore(), maxVectorScore) * properties.getRag().getVectorWeight();
        }

        for (SchemaSearchHit keywordHit : keywordHits) {
            MutableHit hit = merged.computeIfAbsent(keywordHit.document().id(), key -> new MutableHit(keywordHit.document()));
            hit.keywordScore = keywordHit.keywordScore();
            hit.exactTableNameHit = hit.exactTableNameHit || keywordHit.exactTableNameHit();
            hit.finalScore += normalizedScore(keywordHit.keywordScore(), maxKeywordScore) * properties.getRag().getKeywordWeight();
            // 对表名精确命中的候选额外加分，抑制纯向量召回带来的语义漂移。
            if (keywordHit.exactTableNameHit()) {
                hit.finalScore += 0.15;
            }
        }

        for (MutableHit hit : merged.values()) {
            hit.finalScore += tableCommentPrior(hit.document);
        }

        return merged.values().stream()
                .map(MutableHit::toImmutable)
                .sorted(Comparator.comparingDouble(SchemaSearchHit::finalScore).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 你的测试库里已经显式区分了“核心表”和“干扰表”，这里把这个先验纳入最终排序，
     * 避免模型被语义相近但业务无关的干扰表抢走头部位置。
     */
    private double tableCommentPrior(SchemaDocument document) {
        if (!StringUtils.hasText(document.tableComment())) {
            return 0;
        }
        if (document.tableComment().startsWith("核心表-")) {
            return 0.08;
        }
        if (document.tableComment().startsWith("干扰表-")) {
            return -0.08;
        }
        return 0;
    }

    /**
     * 先做宽召回用于选库，再把真正送进 prompt 的表收敛到少量高置信候选，
     * 避免模型在十来张表里来回摇摆，导致生成 SQL 时误连过多表。
     */
    private List<SchemaSearchHit> focusDatasourceHits(List<SchemaSearchHit> datasourceHits) {
        if (datasourceHits.isEmpty()) {
            return List.of();
        }

        int focusLimit = Math.max(1, properties.getRag().getSchemaFocusTopK());
        double topScore = datasourceHits.getFirst().finalScore();
        double scoreThreshold = topScore <= 0 ? 0 : topScore * 0.72;
        Set<String> selectedTableNames = new java.util.LinkedHashSet<>();
        List<SchemaSearchHit> focusedHits = new ArrayList<>();

        for (SchemaSearchHit hit : datasourceHits) {
            String tableName = hit.document().tableName();
            if (selectedTableNames.contains(tableName)) {
                continue;
            }
            if (!hit.exactTableNameHit() && hit.finalScore() < scoreThreshold && !focusedHits.isEmpty()) {
                continue;
            }

            selectedTableNames.add(tableName);
            focusedHits.add(hit);
            if (focusedHits.size() >= focusLimit) {
                break;
            }
        }

        if (!focusedHits.isEmpty()) {
            return focusedHits;
        }
        return datasourceHits.stream()
                .limit(focusLimit)
                .toList();
    }

    private double normalizedScore(double score, double maxScore) {
        if (score <= 0 || maxScore <= 0) {
            return 0;
        }
        return score / maxScore;
    }

    private static final class MutableHit {
        private final SchemaDocument document;
        private double vectorScore;
        private double keywordScore;
        private double finalScore;
        private boolean exactTableNameHit;

        private MutableHit(SchemaDocument document) {
            this.document = document;
        }

        private SchemaSearchHit toImmutable() {
            return new SchemaSearchHit(document, vectorScore, keywordScore, finalScore, exactTableNameHit);
        }
    }
}

package com.yk.demoai.service.impl;

import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaDocument;
import com.yk.demoai.model.SchemaSearchHit;
import com.yk.demoai.service.SchemaIndexStore;
import com.yk.demoai.util.SchemaTermTokenizer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地开发和测试默认使用的内存版 Schema 索引实现。
 */
@Component
@RequiredArgsConstructor
public class InMemorySchemaIndexStoreImpl implements SchemaIndexStore {

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<SchemaDocument> embeddingStore = new InMemoryEmbeddingStore<>();
    private final Map<String, SchemaDocument> documents = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> datasourceToDocumentIds = new ConcurrentHashMap<>();

    @Override
    public synchronized void replaceDatasourceDocuments(DatasourceDescriptor datasource, List<SchemaDocument> newDocuments) {
        Set<String> existingDocumentIds = datasourceToDocumentIds.getOrDefault(datasource.id(), Set.of());
        if (!existingDocumentIds.isEmpty()) {
            embeddingStore.removeAll(existingDocumentIds);
            existingDocumentIds.forEach(documents::remove);
        }

        if (newDocuments.isEmpty()) {
            datasourceToDocumentIds.remove(datasource.id());
            return;
        }

        // 分批处理，每批最多 10 条（DashScope Embedding API 限制）
        int batchSize = 10;
        List<String> allIds = new ArrayList<>();
        List<dev.langchain4j.data.embedding.Embedding> allEmbeddings = new ArrayList<>();
        
        for (int i = 0; i < newDocuments.size(); i += batchSize) {
            List<SchemaDocument> batch = newDocuments.subList(i, Math.min(i + batchSize, newDocuments.size()));
            List<TextSegment> segments = batch.stream()
                    .map(document -> TextSegment.from(document.content()))
                    .toList();
            List<String> ids = batch.stream().map(SchemaDocument::id).toList();
            
            allIds.addAll(ids);
            allEmbeddings.addAll(embeddingModel.embedAll(segments).content());
        }
        
        embeddingStore.addAll(allIds, allEmbeddings, newDocuments);
        newDocuments.forEach(document -> documents.put(document.id(), document));
        datasourceToDocumentIds.put(datasource.id(), new LinkedHashSet<>(allIds));
    }

    @Override
    public List<SchemaSearchHit> vectorSearch(String question, List<String> datasourceIds, int limit) {
        if (documents.isEmpty()) {
            return List.of();
        }

        Set<String> allowedDatasourceIds = datasourceIds == null || datasourceIds.isEmpty()
                ? Set.of()
                : Set.copyOf(datasourceIds);

        List<EmbeddingMatch<SchemaDocument>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed(question).content())
                        .maxResults(Math.max(limit * 5, documents.size()))
                        .minScore(0.0)
                        .build())
                .matches();

        return matches.stream()
                .filter(match -> allowedDatasourceIds.isEmpty() || allowedDatasourceIds.contains(match.embedded().datasourceId()))
                .limit(limit)
                .map(match -> new SchemaSearchHit(match.embedded(), match.score(), 0.0, 0.0, false))
                .toList();
    }

    @Override
    public List<SchemaSearchHit> keywordSearch(String question, List<String> datasourceIds, int limit) {
        Set<String> allowedDatasourceIds = datasourceIds == null || datasourceIds.isEmpty()
                ? Set.of()
                : Set.copyOf(datasourceIds);

        return documents.values().stream()
                .filter(document -> allowedDatasourceIds.isEmpty() || allowedDatasourceIds.contains(document.datasourceId()))
                .map(document -> toKeywordHit(question, document))
                .filter(hit -> hit.keywordScore() > 0)
                .sorted(Comparator.comparingDouble(SchemaSearchHit::keywordScore).reversed())
                .limit(limit)
                .toList();
    }

    private SchemaSearchHit toKeywordHit(String question, SchemaDocument document) {
        String normalizedQuestion = normalize(question);
        String normalizedTableName = normalize(document.tableName());
        String normalizedTableComment = normalize(document.tableComment());
        String normalizedContent = normalize(document.content());
        double score = 0;
        boolean exactTableNameHit = false;

        if (normalizedQuestion.contains(normalizedTableName)) {
            score += 12;
            exactTableNameHit = true;
        }
        if (StringUtils.hasText(document.tableComment()) && normalizedQuestion.contains(normalizedTableComment)) {
            score += 6;
        }

        for (String term : extractSearchTerms(question)) {
            if (term.equalsIgnoreCase(document.tableName())) {
                score += 10;
                exactTableNameHit = true;
                continue;
            }
            if (document.keywords().stream().anyMatch(keyword -> keyword.equalsIgnoreCase(term))) {
                score += 4;
                continue;
            }
            if (normalizedTableComment.contains(term)) {
                score += 2.4;
                continue;
            }
            if (normalizedContent.contains(term)) {
                score += 0.8;
            }
        }

        // 兜底做一次宽松的关键词命中，避免问题只提到表注释里的业务词。
        if (score == 0) {
            for (String keyword : document.keywords()) {
                if (normalizedQuestion.contains(normalize(keyword))) {
                    score += 1.2;
                }
            }
        }

        score += tablePriorScore(document, score > 0, exactTableNameHit);

        return new SchemaSearchHit(document, 0.0, Math.max(score, 0.0), 0.0, exactTableNameHit);
    }

    /**
     * 问题里的中英文业务词会一起参与关键词匹配，避免中文问题只能靠向量检索。
     */
    private Collection<String> extractSearchTerms(String question) {
        return new ArrayList<>(new LinkedHashSet<>(SchemaTermTokenizer.extractQueryTerms(question)));
    }

    private double tablePriorScore(SchemaDocument document, boolean hasSemanticMatch, boolean exactTableNameHit) {
        if (!hasSemanticMatch || exactTableNameHit || !StringUtils.hasText(document.tableComment())) {
            return 0;
        }
        if (document.tableComment().startsWith("核心表-")) {
            return 1.6;
        }
        if (document.tableComment().startsWith("干扰表-")) {
            return -1.4;
        }
        return 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

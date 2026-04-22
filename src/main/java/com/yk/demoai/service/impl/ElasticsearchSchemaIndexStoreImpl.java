package com.yk.demoai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.model.SchemaDocument;
import com.yk.demoai.model.SchemaSearchHit;
import com.yk.demoai.service.SchemaIndexStore;
import com.yk.demoai.util.SchemaTermTokenizer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 ElasticSearch 的 Schema 索引实现，支持向量召回与关键字召回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = "elasticsearchRestTemplate")
public class ElasticsearchSchemaIndexStoreImpl implements SchemaIndexStore {

    private final RestTemplate elasticsearchRestTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final AtomicBoolean indexReady = new AtomicBoolean(false);

    @Override
    public synchronized void replaceDatasourceDocuments(DatasourceDescriptor datasource, List<SchemaDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }

        // 分批处理，每批最多 10 条（DashScope Embedding API 限制）
        int batchSize = 10;
        List<Embedding> allEmbeddings = new ArrayList<>();
        
        for (int i = 0; i < documents.size(); i += batchSize) {
            List<SchemaDocument> batch = documents.subList(i, Math.min(i + batchSize, documents.size()));
            List<TextSegment> segments = batch.stream()
                    .map(document -> TextSegment.from(document.content()))
                    .toList();
            allEmbeddings.addAll(embeddingModel.embedAll(segments).content());
        }
        
        ensureIndex(allEmbeddings.getFirst().dimension());
        deleteDatasourceDocuments(datasource.id());
        bulkIndex(documents, allEmbeddings);
    }

    @Override
    public List<SchemaSearchHit> vectorSearch(String question, List<String> datasourceIds, int limit) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", limit);
        request.put("_source", sourceFields());

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("source", "cosineSimilarity(params.queryVector, 'embedding') + 1.0");
        script.put("params", Map.of("queryVector", queryEmbedding.vectorAsList()));

        request.put("query", Map.of(
                "script_score", Map.of(
                        "query", filterQuery(datasourceIds),
                        "script", script
                )
        ));

        return search(request, ScoreType.VECTOR);
    }

    @Override
    public List<SchemaSearchHit> keywordSearch(String question, List<String> datasourceIds, int limit) {
        List<Map<String, Object>> shouldClauses = new ArrayList<>();
        shouldClauses.add(Map.of(
                "multi_match", Map.of(
                        "query", question,
                        "fields", List.of("tableName^6", "tableComment^4", "keywords^5", "content^2")
                )
        ));

        for (String term : SchemaTermTokenizer.extractQueryTerms(question)) {
            shouldClauses.add(Map.of(
                    "term", Map.of(
                            "keywords", Map.of(
                                    "value", term,
                                    "boost", 8
                            )
                    )
            ));
            shouldClauses.add(Map.of(
                    "match_phrase", Map.of(
                            "tableComment", Map.of(
                                    "query", term,
                                    "boost", 4
                            )
                    )
            ));
        }

        for (String identifier : SchemaTermTokenizer.extractQueryTerms(question).stream()
                .filter(term -> term.matches("[A-Za-z_][A-Za-z0-9_]*"))
                .toList()) {
            shouldClauses.add(Map.of(
                    "term", Map.of(
                            "tableName.keyword", Map.of(
                                    "value", identifier,
                                    "boost", 10
                            )
                    )
            ));
        }

        Map<String, Object> boolQuery = new LinkedHashMap<>();
        boolQuery.put("filter", filterClauses(datasourceIds));
        boolQuery.put("should", shouldClauses);
        boolQuery.put("minimum_should_match", 1);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", limit);
        request.put("_source", sourceFields());
        request.put("query", Map.of("bool", boolQuery));

        return search(request, ScoreType.KEYWORD);
    }

    private void ensureIndex(int dimension) {
        if (indexReady.get()) {
            return;
        }

        String url = indexUrl();
        try {
            elasticsearchRestTemplate.exchange(url, HttpMethod.HEAD, HttpEntity.EMPTY, String.class);
            indexReady.set(true);
            return;
        } catch (HttpClientErrorException.NotFound ignored) {
            // 索引不存在时按当前 embedding 维度自动创建。
        }

        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("id", Map.of("type", "keyword"));
        propertiesMap.put("datasourceId", Map.of("type", "keyword"));
        propertiesMap.put("datasourceName", Map.of("type", "keyword"));
        propertiesMap.put("catalog", Map.of("type", "keyword"));
        propertiesMap.put("schemaName", Map.of("type", "keyword"));
        propertiesMap.put("tableName", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))));
        propertiesMap.put("tableComment", Map.of("type", "text"));
        propertiesMap.put("keywords", Map.of("type", "keyword"));
        propertiesMap.put("content", Map.of("type", "text"));
        propertiesMap.put("embedding", Map.of("type", "dense_vector", "dims", dimension, "index", false));

        Map<String, Object> body = Map.of("mappings", Map.of("properties", propertiesMap));
        elasticsearchRestTemplate.exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(body, jsonHeaders()),
                String.class
        );
        indexReady.set(true);
    }

    private void deleteDatasourceDocuments(String datasourceId) {
        Map<String, Object> request = Map.of(
                "query", Map.of(
                        "term", Map.of("datasourceId", datasourceId)
                )
        );
        elasticsearchRestTemplate.postForEntity(
                indexUrl() + "/_delete_by_query",
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );
    }

    private void bulkIndex(List<SchemaDocument> documents, List<Embedding> embeddings) {
        StringBuilder bulkBody = new StringBuilder();
        for (int index = 0; index < documents.size(); index++) {
            SchemaDocument document = documents.get(index);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("id", document.id());
            source.put("datasourceId", document.datasourceId());
            source.put("datasourceName", document.datasourceName());
            source.put("catalog", document.catalog());
            source.put("schemaName", document.schemaName());
            source.put("tableName", document.tableName());
            source.put("tableComment", document.tableComment());
            source.put("keywords", document.keywords());
            source.put("content", document.content());
            source.put("embedding", embeddings.get(index).vectorAsList());

            try {
                bulkBody.append(objectMapper.writeValueAsString(Map.of(
                                "index", Map.of("_index", properties.getElasticsearch().getIndexName(), "_id", document.id()))))
                        .append('\n')
                        .append(objectMapper.writeValueAsString(source))
                        .append('\n');
            } catch (Exception ex) {
                throw new IllegalStateException("构建 ElasticSearch 索引请求失败", ex);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "x-ndjson", StandardCharsets.UTF_8));
        ResponseEntity<String> response = elasticsearchRestTemplate.postForEntity(
                baseUrl() + "/_bulk?refresh=true",
                new HttpEntity<>(bulkBody.toString(), headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("ElasticSearch bulk index 失败: " + response.getStatusCode());
        }
    }

    private List<SchemaSearchHit> search(Map<String, Object> request, ScoreType scoreType) {
        ResponseEntity<String> response = elasticsearchRestTemplate.postForEntity(
                indexUrl() + "/_search",
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode hits = root.path("hits").path("hits");
            List<SchemaSearchHit> results = new ArrayList<>();
            for (JsonNode hitNode : hits) {
                SchemaDocument document = parseDocument(hitNode.path("_source"));
                double score = hitNode.path("_score").asDouble(0.0);
                boolean exactTableNameHit = scoreType == ScoreType.KEYWORD
                        && containsIdentifierMatch(document.tableName(), request);
                results.add(new SchemaSearchHit(
                        document,
                        scoreType == ScoreType.VECTOR ? score : 0.0,
                        scoreType == ScoreType.KEYWORD ? score : 0.0,
                        0.0,
                        exactTableNameHit
                ));
            }
            return results;
        } catch (Exception ex) {
            throw new IllegalStateException("解析 ElasticSearch 查询结果失败", ex);
        }
    }

    private boolean containsIdentifierMatch(String tableName, Map<String, Object> request) {
        Object queryNode = request.get("query");
        if (!(queryNode instanceof Map)) {
            return false;
        }
        Map queryMap = (Map) queryNode;
        Object boolNode = queryMap.get("bool");
        if (!(boolNode instanceof Map)) {
            return false;
        }
        Map boolMap = (Map) boolNode;
        Object shouldNode = boolMap.get("should");
        if (!(shouldNode instanceof List)) {
            return false;
        }
        List shouldClauses = (List) shouldNode;
        for (Object shouldClause : shouldClauses) {
            if (!(shouldClause instanceof Map)) {
                continue;
            }
            Map clause = (Map) shouldClause;
            Object termNode = clause.get("term");
            if (!(termNode instanceof Map)) {
                continue;
            }
            Map termMap = (Map) termNode;
            Object tableNameKeywordNode = termMap.get("tableName.keyword");
            if (tableNameKeywordNode instanceof Map) {
                Map keywordMap = (Map) tableNameKeywordNode;
                Object value = keywordMap.get("value");
                if (value != null && tableName.equalsIgnoreCase(String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    private SchemaDocument parseDocument(JsonNode source) {
        List<String> keywords = new ArrayList<>();
        source.path("keywords").forEach(keyword -> keywords.add(keyword.asText()));
        return new SchemaDocument(
                source.path("id").asText(),
                source.path("datasourceId").asText(),
                source.path("datasourceName").asText(),
                source.path("catalog").asText(""),
                source.path("schemaName").asText(""),
                source.path("tableName").asText(),
                source.path("tableComment").asText(""),
                keywords,
                source.path("content").asText()
        );
    }

    private Map<String, Object> filterQuery(List<String> datasourceIds) {
        List<Map<String, Object>> filters = filterClauses(datasourceIds);
        if (filters.isEmpty()) {
            return Map.of("match_all", Map.of());
        }
        return Map.of("bool", Map.of("filter", filters));
    }

    private List<Map<String, Object>> filterClauses(List<String> datasourceIds) {
        if (datasourceIds == null || datasourceIds.isEmpty()) {
            return List.of();
        }
        return List.of(Map.of("terms", Map.of("datasourceId", datasourceIds)));
    }

    private List<String> sourceFields() {
        return List.of("id", "datasourceId", "datasourceName", "catalog", "schemaName",
                "tableName", "tableComment", "keywords", "content");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String baseUrl() {
        return properties.getElasticsearch().getBaseUrl().replaceAll("/+$", "");
    }

    private String indexUrl() {
        return baseUrl() + "/" + properties.getElasticsearch().getIndexName();
    }

    private enum ScoreType {
        VECTOR,
        KEYWORD
    }
}

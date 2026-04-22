package com.yk.demoai.configure;

import com.yk.demoai.service.SchemaIndexStore;
import com.yk.demoai.service.impl.ElasticsearchSchemaIndexStoreImpl;
import com.yk.demoai.service.impl.InMemorySchemaIndexStoreImpl;
import com.yk.demoai.service.impl.ResilientSchemaIndexStoreImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RagConfiguration {

    @Bean
    public SchemaIndexStore schemaIndexStore(AppProperties properties,
                                             InMemorySchemaIndexStoreImpl inMemorySchemaIndexStore,
                                             ObjectProvider<ElasticsearchSchemaIndexStoreImpl> elasticsearchSchemaIndexStoreProvider) {
        ElasticsearchSchemaIndexStoreImpl elasticsearchSchemaIndexStore = elasticsearchSchemaIndexStoreProvider.getIfAvailable();
        if (properties.getElasticsearch().isEnabled() && elasticsearchSchemaIndexStore != null) {
            return new ResilientSchemaIndexStoreImpl(
                    elasticsearchSchemaIndexStore,
                    inMemorySchemaIndexStore,
                    properties.getElasticsearch().isAllowInMemoryFallback()
            );
        }
        return inMemorySchemaIndexStore;
    }

    @Bean(name = "elasticsearchRestTemplate")
    @ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
    public RestTemplate elasticsearchRestTemplate(RestTemplateBuilder builder, AppProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getElasticsearch().getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getElasticsearch().getReadTimeout().toMillis());

        RestTemplateBuilder restTemplateBuilder = builder.requestFactory(() -> requestFactory);
        if (StringUtils.hasText(properties.getElasticsearch().getUsername())) {
            restTemplateBuilder = restTemplateBuilder.basicAuthentication(
                    properties.getElasticsearch().getUsername(),
                    properties.getElasticsearch().getPassword()
            );
        }
        return restTemplateBuilder.build();
    }
}

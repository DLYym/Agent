package com.yk.demoai.service;

import com.yk.demoai.configure.AppProperties;
import com.yk.demoai.model.DatasourceDescriptor;
import com.yk.demoai.service.impl.DynamicDataSourceManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class DynamicDataSourceManagerTest {

    private final AppProperties properties = new AppProperties();
    private final DynamicDataSourceManager manager;

    DynamicDataSourceManagerTest() {
        properties.getDatasourceCache().setMaxSize(1);
        manager = new DynamicDataSourceManagerImpl(properties);
    }

    @AfterEach
    void tearDown() {
        manager.invalidateAll();
    }

    @Test
    void shouldReuseDataSourceAndEvictLeastRecentlyUsedPool() {
        DatasourceDescriptor firstDescriptor = descriptor("cache-a",
                "jdbc:h2:mem:cache_a;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        DatasourceDescriptor secondDescriptor = descriptor("cache-b",
                "jdbc:h2:mem:cache_b;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");

        DataSource firstDataSource = manager.getDataSource(firstDescriptor);
        DataSource cachedDataSource = manager.getDataSource(firstDescriptor);
        assertSame(firstDataSource, cachedDataSource);

        manager.getDataSource(secondDescriptor);
        manager.cleanUp();
        DataSource reloadedFirstDataSource = manager.getDataSource(firstDescriptor);

        assertNotSame(firstDataSource, reloadedFirstDataSource);
    }

    private DatasourceDescriptor descriptor(String id, String url) {
        return new DatasourceDescriptor(id, id, url, "sa", "", null, null, null, List.of());
    }
}

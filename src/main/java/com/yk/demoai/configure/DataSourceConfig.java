package com.yk.demoai.configure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        // 硬编码 MySQL 连接信息
        config.setJdbcUrl("jdbc:mysql://localhost:3306/loan_business_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        config.setUsername("root");
        config.setPassword("august.24");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(120000);
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        return new HikariDataSource(config);
    }
}

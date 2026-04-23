package com.yk.demoai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@MapperScan("com.yk.demoai.mapper")
@SpringBootApplication
public class DemoAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoAiApplication.class, args);
    }

}

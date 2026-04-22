package com.yk.demoai.service;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * 基于提示词模板生成 SQL 的 AI Service。
 */
@AiService
public interface SqlGenerator {

    /**
     * 仅基于目标数据源的 Schema 上下文生成 SQL。
     */
    @SystemMessage(fromResource = "sql_prompt.md")
    String generate(@V("schema") String schema,
                    @V("dbType") String dbType,
                    @UserMessage String question);
}

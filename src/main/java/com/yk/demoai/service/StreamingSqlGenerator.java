package com.yk.demoai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 负责把 SQL 生成过程按 token 流式输出，供前端实时展示。
 */
public interface StreamingSqlGenerator {

    /**
     * 基于目标数据源的 Schema 上下文流式生成 SQL。
     */
    @SystemMessage(fromResource = "sql_prompt.md")
    TokenStream generate(@V("schema") String schema,
                         @V("dbType") String dbType,
                         @UserMessage String question);
}

package com.yk.demoai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 在 SQL 校验或执行失败后，按 token 流式输出修复结果。
 */
public interface StreamingSqlRepairGenerator {

    /**
     * 基于失败反馈流式修复上一版 SQL。
     */
    @SystemMessage(fromResource = "sql_repair_prompt.md")
    TokenStream repair(@V("schema") String schema,
                       @V("dbType") String dbType,
                       @V("question") String question,
                       @V("previousSql") String previousSql,
                       @UserMessage String feedback);
}

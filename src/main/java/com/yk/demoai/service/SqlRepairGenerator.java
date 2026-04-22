package com.yk.demoai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * 在 SQL 校验或执行失败后，基于反馈信息生成修复后的 SQL。
 */
@AiService
public interface SqlRepairGenerator {

    /**
     * 根据问题、Schema 和失败反馈重新生成 SQL。
     */
    @SystemMessage(fromResource = "sql_repair_prompt.md")
    String repair(@V("schema") String schema,
                  @V("dbType") String dbType,
                  @V("question") String question,
                  @V("previousSql") String previousSql,
                  @UserMessage String feedback);
}

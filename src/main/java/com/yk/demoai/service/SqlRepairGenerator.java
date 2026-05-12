package com.yk.demoai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SqlRepairGenerator {

    @SystemMessage(fromResource = "sql_repair_prompt.md")
    String repair(@V("schema") String schema,
                  @V("dbType") String dbType,
                  @V("logicalRelations") String logicalRelations,
                  @V("foreignKeyReplacementRules") String foreignKeyReplacementRules,
                  @V("dictMappingReplacementRules") String dictMappingReplacementRules,
                  @V("question") String question,
                  @V("previousSql") String previousSql,
                  @UserMessage String feedback);
}

package com.yk.demoai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StreamingSqlRepairGenerator {

    @SystemMessage(fromResource = "sql_repair_prompt.md")
    TokenStream repair(@V("schema") String schema,
                       @V("dbType") String dbType,
                       @V("logicalRelations") String logicalRelations,
                       @V("foreignKeyReplacementRules") String foreignKeyReplacementRules,
                       @V("dictMappingReplacementRules") String dictMappingReplacementRules,
                       @V("question") String question,
                       @V("previousSql") String previousSql,
                       @UserMessage String feedback);
}

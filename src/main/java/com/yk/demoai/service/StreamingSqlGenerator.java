package com.yk.demoai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface StreamingSqlGenerator {

    @SystemMessage(fromResource = "sql_prompt.md")
    TokenStream generate(@V("schema") String schema,
                         @V("dbType") String dbType,
                         @V("logicalRelations") String logicalRelations,
                         @UserMessage String question);
}

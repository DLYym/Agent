package com.yk.demoai.tool;

import dev.langchain4j.agent.tool.Tool;

/**
 * @ClassName Tools
 * @Description
 * @Author wangyanhui
 * @Date 2026/4/22 10:14
 * @Version 1.0
 **/
public class Tools {

    @Tool("test")
    public String test(){
        return "test";
    }
}

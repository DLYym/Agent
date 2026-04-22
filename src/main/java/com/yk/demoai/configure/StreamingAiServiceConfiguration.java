package com.yk.demoai.configure;

import com.yk.demoai.service.StreamingSqlGenerator;
import com.yk.demoai.service.StreamingSqlRepairGenerator;
import com.yk.demoai.tool.Tools;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skills;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

/**
 * 为真正的 token streaming 显式创建流式 AI Service Bean。
 */
@Configuration
@Slf4j
public class StreamingAiServiceConfiguration {
    private String MCP_REMOTE_SERVER_URL = "";
    private String SKILLS_DIRECTORY = "src/main/resources/skills";
    /**
     * 使用流式聊天模型构建 SQL 生成器，便于前端逐 token 展示生成过程。
     */
    @Bean
    public StreamingSqlGenerator streamingSqlGenerator(StreamingChatModel streamingChatModel) {
//        // 1. 加载 MCP 服务
//        McpTransport transport = StreamableHttpMcpTransport.builder()
//                .url(MCP_REMOTE_SERVER_URL)
//                .build();
//
//        // MCP 工具提供者（提供数据库操作相关工具）
//        ToolProvider mcpToolProvider = McpToolProvider.builder()
//                .mcpClients(DefaultMcpClient.builder()
//                        .key("MysqlSkillsMcp")
//                        .transport(transport)
//                        .build())
//                .build();


        // 从文件系统加载 Skills（包含 database-ops 等技能）
        Skills skills = Skills.from(FileSystemSkillLoader.loadSkills(Path.of(SKILLS_DIRECTORY)));

        // 工具调用监听器，记录每次工具调用的名称和来源，用于区分 Skills 触发还是直接触发
        // 因为即使不通过 Skill 该逻辑也是能跑通，这里为了验证确实激活了 Skills 打印了下面的日志。
        ToolExecutedEventListener toolExecutedListener = event -> {
            String toolName = event.request().name();
            String arguments = event.request().arguments();

            if ("activate_skill".equals(toolName)) {
                log.info("【Skills 触发】激活技能，参数：{}", arguments);
            } else if ("read_skill_resource".equals(toolName)) {
                log.info("【Skills 触发】读取技能资源，参数：{}", arguments);
            } else {
                log.info("【MCP 工具调用】工具名：{}，参数：{}", toolName, arguments);
            }

            // 打印工具执行结果
            String resultText = event.resultText();
            log.info("【工具执行结果】{}", resultText);
        };



        return AiServices.builder(StreamingSqlGenerator.class)
                .streamingChatModel(streamingChatModel)
                .toolProvider(CompositeToolProvider.of(skills.toolProvider()))
                .tools(new Tools())
                .build();
    }

    /**
     * 修复 SQL 时同样保留流式输出能力，避免第二轮生成退回整段返回。
     */
    @Bean
    public StreamingSqlRepairGenerator streamingSqlRepairGenerator(StreamingChatModel streamingChatModel) {
        return AiServices.builder(StreamingSqlRepairGenerator.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}

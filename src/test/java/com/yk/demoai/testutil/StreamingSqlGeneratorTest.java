package com.yk.demoai.testutil;
import com.yk.demoai.service.StreamingSqlGenerator;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * @ClassName StreamingSqlGeneratorTest
 * @Description
 * @Author wangyanhui
 * @Date 2026/4/22 16:56
 * @Version 1.0
 **/

@SpringBootTest
public class StreamingSqlGeneratorTest {

    @Autowired
    private StreamingSqlGenerator streamingSqlGenerator;

    @Test
    public void testGenerateSqlWithSkills() throws InterruptedException {
        // 1. 测试数据
        String schema = "user(id int, name varchar(255), age int)";
        String dbType = "mysql";
        String question = "查询所有年龄大于18的用户";

        // 2. 获取 TokenStream
        TokenStream tokenStream = streamingSqlGenerator.generate(schema, dbType, question);

        // 2. 关键：让测试程序 等待 AI 返回，不立即结束
        CountDownLatch latch = new CountDownLatch(1);

        // 3. 严格按照你提供的官方 API 编写（完全匹配）
        StringBuilder result = new StringBuilder();

        tokenStream
                // 接收每一段返回内容
                .onPartialResponse(token -> {
                    result.append(token);
                    System.out.print(token);
                })
                // 接收最终完整响应
                .onCompleteResponse(response -> {
                    System.out.println("\n\n✅ 最终结果：\n" + result);
                    latch.countDown(); // 通知结束
                })
                // 错误处理
                .onError(Throwable::printStackTrace)
                // 启动流（必须调用！）
                .start();
        latch.await();
    }
}

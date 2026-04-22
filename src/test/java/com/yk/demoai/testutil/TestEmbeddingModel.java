package com.yk.demoai.testutil;


import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.FileSystemSkill;
import dev.langchain4j.skills.FileSystemSkillLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


@SpringBootTest
public class TestEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    public TestEmbeddingModel() {
        this.dimension = 16;
    }


//    public TestEmbeddingModel(int dimension) {
//        this.dimension = dimension;
//    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>(textSegments.size());
        for (TextSegment textSegment : textSegments) {
            embeddings.add(embedInternal(textSegment.text()));
        }
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return "test-embedding-model";
    }

    private Embedding embedInternal(String text) {
        float[] vector = new float[dimension];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (char character : normalized.toCharArray()) {
            vector[Math.floorMod(character, dimension)] += 1F;
        }

        double magnitude = 0;
        for (float value : vector) {
            magnitude += value * value;
        }
        magnitude = Math.sqrt(magnitude);
        if (magnitude > 0) {
            for (int index = 0; index < vector.length; index++) {
                vector[index] /= (float) magnitude;
            }
        }
        return Embedding.from(vector);
    }

    interface ExplainerService {
        String explainCode(String filePath);
    }

    @Test
    void testSkills() throws URISyntaxException {
//        // 1. 加载 Skills
//        List<FileSystemSkill> skillList =
//                FileSystemSkillLoader.loadSkills(Path.of("skills/"));
//        Skills skills = Skills.from(skillList);
//
//        // 2. 创建 AI Service
//        ExplainerService service = AiServices.builder(ExplainerService.class)
//                .chatModel(chatModel)
//                .tools(new CodeAnalysisTools())  // 注册工具
//                .toolProvider(skills.toolProvider())  // 注册 Skills
//                .systemMessage(
//                        "你是一个代码分析助手。\n" +
//                                "你可以使用以下技能:\n" +
//                                skills.formatAvailableSkills() +
//                                "\n当用户要求解释代码时,使用 activate_skill 工具激活 explain-code 技能。"
//                )
//                .build();
//
//        // 3. 使用
//        String result = service.explainCode("src/main/java/Example.java");
//        System.out.println(result);
        // 加载指定目录下的所有Skill
        URL resource = getClass().getClassLoader().getResource("skills");
        Path skillsPath = Paths.get(resource.toURI());
        getClass().getClassLoader().getResource("");
        List<FileSystemSkill> skills = FileSystemSkillLoader.loadSkills(skillsPath);
        System.out.println(skills);
        // 加载单个Skill
        FileSystemSkill codeSkill = FileSystemSkillLoader.loadSkill(Path.of("skills/explain-code"));
        System.out.println(codeSkill);
        List<FileSystemSkill> skills2 = ClassPathSkillLoader.loadSkills("skills/");
        System.out.println(skills2);
        FileSystemSkill codeSkill2 = ClassPathSkillLoader.loadSkill("skills/explain-code");
        System.out.println(codeSkill2);

    }



    @Test
    void testSkills2(){
//        String chat = skillsAssistant.chat("你有哪些技能？");
//        System.out.println(chat);

    }
}

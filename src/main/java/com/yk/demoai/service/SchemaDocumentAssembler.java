package com.yk.demoai.service;

import com.yk.demoai.model.SchemaDocument;
import com.yk.demoai.model.SchemaSnapshot;

import java.util.List;

/**
 * 将数据库 Schema 快照转换成检索和提示词可消费的文本片段。
 */
public interface SchemaDocumentAssembler {

    /**
     * 将 Schema 快照展开为表级文档。
     */
    List<SchemaDocument> toDocuments(SchemaSnapshot snapshot);

    /**
     * 将整个 Schema 快照拼接成适合喂给模型的文本。
     */
    String toPromptText(SchemaSnapshot snapshot);
}

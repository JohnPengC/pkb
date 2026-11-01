package com.pkb.knowledge;

import java.util.UUID;

/** 文档创建或保存后发布，触发（重新）向量化。 */
public record DocumentSavedEvent(UUID documentId) {
}

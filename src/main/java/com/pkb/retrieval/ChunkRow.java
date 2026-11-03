package com.pkb.retrieval;

import java.util.UUID;

/** chunkType: DISTILLED（提炼文档块）/ RAW（原文块） */
public record ChunkRow(UUID id, UUID documentId, String content, double distance, String chunkType) {
}

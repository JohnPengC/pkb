package com.pkb.retrieval;

import java.util.UUID;

public record ChunkRow(UUID id, UUID documentId, String content, double distance) {
}

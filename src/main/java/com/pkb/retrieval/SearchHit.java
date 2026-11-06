package com.pkb.retrieval;

import java.util.UUID;

public record SearchHit(UUID documentId, String title, String snippet, double score, String evidence) {
}

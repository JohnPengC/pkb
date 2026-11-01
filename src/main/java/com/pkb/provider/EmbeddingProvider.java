package com.pkb.provider;

import java.util.List;

public interface EmbeddingProvider {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);
    /** 当前 embedding 模型标识，写入 document_chunk.embedding_model */
    String modelId();
}

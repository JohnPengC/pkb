package com.pkb.provider;

import org.springframework.stereotype.Component;

/**
 * 运行期模型配置持有者。Chat 模型支持热切换（Task 12 的管理接口写这里）；
 * Embedding 不允许静默热切换——必须走 Task 12 的全量重建流程。
 */
@Component
public class ModelConfigHolder {

    private volatile ModelProperties.ModelEndpoint chat;
    private final ModelProperties.ModelEndpoint embedding;

    public ModelConfigHolder(ModelProperties properties) {
        this.chat = properties.chat();
        this.embedding = properties.embedding();
    }

    public ModelProperties.ModelEndpoint currentChat() {
        return chat;
    }

    public ModelProperties.ModelEndpoint currentEmbedding() {
        return embedding;
    }

    public void switchChat(String baseUrl, String apiKey, String model) {
        this.chat = new ModelProperties.ModelEndpoint(baseUrl, apiKey, model);
    }
}

package com.pkb.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pkb.model")
public record ModelProperties(ModelEndpoint chat, ModelEndpoint embedding) {
    public record ModelEndpoint(String baseUrl, String apiKey, String model) {
    }
}

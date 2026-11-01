package com.pkb.provider.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class OpenAiDtos {

    public record ChatRequest(String model, List<Message> messages, boolean stream) {
        public record Message(String role, String content) {
        }
    }

    public record ChatResponse(List<Choice> choices) {
        public record Choice(Message message, Delta delta) {
            public record Message(String content) {
            }
            public record Delta(String content) {
            }
        }
    }

    public record EmbeddingRequest(String model, List<String> input) {
    }

    public record EmbeddingResponse(List<Entry> data) {
        public record Entry(@JsonProperty("embedding") List<Double> embedding) {
        }
    }

    private OpenAiDtos() {
    }
}

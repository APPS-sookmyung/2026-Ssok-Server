package com.ssok.server.domain.conversation.entity;

public enum MessageRole {
    USER,
    ASSISTANT;

    public String aiValue() {
        return name().toLowerCase();
    }
}

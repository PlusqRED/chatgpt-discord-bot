package com.aleh.gpt.discord.app.dto.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record Message(String role, String content) {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Message(String role, String content) {
        this.role = role;
        this.content = content.replaceAll("==", "");
    }

    public static Message fromJson(String json) {
        try {
            return objectMapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting JSON to record", e);
        }
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting record to JSON", e);
        }
    }
}
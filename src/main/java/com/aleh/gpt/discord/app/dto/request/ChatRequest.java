package com.aleh.gpt.discord.app.dto.request;

import java.util.List;

public record ChatRequest(String model, List<Message> messages, boolean stream) {
}

package com.aleh.gpt.discord.app.client;

import com.aleh.gpt.discord.app.dto.request.ChatRequest;
import com.aleh.gpt.discord.app.dto.response.ChatCompletionChunk;
import reactor.core.publisher.Flux;

public interface ChatClient {
    Flux<ChatCompletionChunk> chatCompletions(ChatRequest chatRequest);
}

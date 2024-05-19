package com.aleh.gpt.discord.app.client;

import com.aleh.gpt.discord.app.dto.request.ChatRequest;
import com.aleh.gpt.discord.app.dto.response.ChatCompletionChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.IOException;

@Service
public class ChatGptClient implements ChatClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;
    @Value("${openai.api-key}")
    private String openApiKey;

    public ChatGptClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Flux<ChatCompletionChunk> chatCompletions(ChatRequest chatRequest) {
        return webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + openApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(chatRequest)
                .retrieve()
                .bodyToFlux(String.class)
                .takeWhile(jsonLine -> !jsonLine.trim().equals("[DONE]"))
                .filter(this::isValidJson)
                .<ChatCompletionChunk>handle((json, sink) -> {
                    try {
                        sink.next(objectMapper.readValue(json, ChatCompletionChunk.class));
                    } catch (JsonProcessingException e) {
                        sink.error(new RuntimeException(e));
                    }
                })
                .filter(chatCompletionChunk -> chatCompletionChunk.choices().getFirst().finishReason() == null);
    }

    private boolean isValidJson(String jsonLine) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonLine);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}

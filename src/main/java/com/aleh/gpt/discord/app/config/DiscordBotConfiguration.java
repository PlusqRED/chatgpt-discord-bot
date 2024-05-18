package com.aleh.gpt.discord.app.config;

import com.aleh.gpt.discord.app.listener.EventListener;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class DiscordBotConfiguration {

    @Value("${discord.bot.token}")
    private String discordBotToken;

    @Value("${spring.ai.openai.api-key}")
    private String openAiToken;

    @Bean
    public <T extends Event> GatewayDiscordClient gatewayDiscordClient(List<EventListener<T>> eventListeners) {
        GatewayDiscordClient client = DiscordClientBuilder.create(discordBotToken)
                .build()
                .login()
                .block();

        for (EventListener<T> listener : eventListeners) {
            assert client != null;
            client.on(listener.getEventType())
                    .flatMap(listener::processEvent)
                    .onErrorResume(listener::handleError)
                    .subscribe();
        }

        return client;
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ChatClient chatClient() {
        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setModel("gpt-3.5-turbo-0125");
        options.setMaxTokens(300);
        return new OpenAiChatClient(new OpenAiApi(openAiToken), options);
    }

}

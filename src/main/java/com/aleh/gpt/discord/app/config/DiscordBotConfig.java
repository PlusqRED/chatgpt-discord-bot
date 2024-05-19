package com.aleh.gpt.discord.app.config;

import com.aleh.gpt.discord.app.listener.EventListener;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DiscordBotConfig {

    @Value("${discord.bot.token}")
    private String discordBotToken;

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
}

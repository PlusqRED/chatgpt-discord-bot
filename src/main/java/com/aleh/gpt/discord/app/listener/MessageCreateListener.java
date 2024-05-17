package com.aleh.gpt.discord.app.listener;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageEditSpec;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class MessageCreateListener implements EventListener<MessageCreateEvent> {

    private final OpenAiChatClient chatClient;

    @Autowired
    public MessageCreateListener(OpenAiChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Class<MessageCreateEvent> getEventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Flux<Void> execute(MessageCreateEvent event) {
        var userMessage = getUserMessage(event);
        StringBuilder combinedStringBuilder = new StringBuilder();
        return sendThinkingResponseMessage(userMessage).flux()
                .flatMap(message -> updateMessageContent(message, streamAndFilterChatMessages(userMessage), combinedStringBuilder))
                .then()
                .flux();
    }

    private Mono<Message> getUserMessage(MessageCreateEvent event) {
        return Mono.just(event.getMessage())
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(false))
                .filter(message -> message.getContent().startsWith("=="));
    }

    private Mono<Message> sendThinkingResponseMessage(Mono<Message> getUserMessage) {
        return getUserMessage
                .flatMap(message -> message.getChannel().flatMap(channel -> channel.createMessage("Думаю, что бы такого тебе ответить...")));
    }

    private Flux<List<String>> streamAndFilterChatMessages(Mono<Message> getUserMessage) {
        return getUserMessage.flux()
                .flatMap(message -> chatClient.stream(message.getContent()))
                .filter(partOfTheMessage -> !partOfTheMessage.isBlank())
                .buffer(40);
    }

    private Flux<Message> updateMessageContent(Message message, Flux<List<String>> sendUserMessageToChatClientStream, StringBuilder combinedStringBuilder) {
        return sendUserMessageToChatClientStream.flatMap(partOfTheMessage ->
                message.edit(MessageEditSpec.builder().content(combineStrings(partOfTheMessage, combinedStringBuilder)).build())
        );
    }

    private String combineStrings(List<String> partOfTheMessage, StringBuilder combinedStringBuilder) {
        return partOfTheMessage.stream().collect(() -> combinedStringBuilder, StringBuilder::append, StringBuilder::append).toString();
    }

}
package com.aleh.gpt.discord.app.listener;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.MessageEditSpec;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MessageCreateListener implements EventListener<MessageCreateEvent> {

    @Value("${discord.bot.answering-speed}")
    private String discordBotAnsweringSpeed;

    private final OpenAiChatClient chatClient;

    private final AtomicInteger servedUsersCount = new AtomicInteger(0);

    @Autowired
    public MessageCreateListener(OpenAiChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Class<MessageCreateEvent> getEventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Flux<Void> processEvent(MessageCreateEvent event) {
        var userMessage = getUserMessage(event);
        StringBuilder combinedStringBuilder = new StringBuilder();
        return sendThinkingResponseMessage(userMessage).flux()
                .flatMap(message -> updateMessageContent(message, streamAndFilterChatMessages(message), combinedStringBuilder))
                .doOnComplete(() -> logResultContent(combinedStringBuilder))
                .then()
                .flux();
    }

    private void logResultContent(StringBuilder combinedStringBuilder) {
        if (!combinedStringBuilder.isEmpty()) {
            LOG.info("[{}] Combined response retrieved: Content: {}", servedUsersCount.incrementAndGet(), combinedStringBuilder);
        }
    }

    private Mono<Message> getUserMessage(MessageCreateEvent event) {
        return Mono.just(event.getMessage())
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(true))
                .filter(message -> message.getContent().startsWith("=="))
                .doOnNext(message -> LOG.info("Request retrieved. Author: {}, Content: {}",
                        message.getAuthor().map(User::getUsername).orElse("Hidden Username"),
                        message.getContent()
                ));
    }

    private Mono<Message> sendThinkingResponseMessage(Mono<Message> getUserMessage) {
        return getUserMessage
                .flatMap(message -> message.getChannel().flatMap(channel -> channel.createMessage("Думаю, что бы такого тебе ответить...")));
    }

    private Flux<List<String>> streamAndFilterChatMessages(Message message) {
        return chatClient.stream(message.getContent())
                .filter(partOfTheMessage -> !partOfTheMessage.isBlank())
                .buffer(Integer.parseInt(discordBotAnsweringSpeed));
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
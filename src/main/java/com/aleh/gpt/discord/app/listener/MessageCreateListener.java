package com.aleh.gpt.discord.app.listener;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.possible.Possible;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MessageCreateListener implements EventListener<MessageCreateEvent> {

    private final String discordBotAnsweringSpeed;
    private final OpenAiChatClient chatClient;
    private final AtomicInteger servedUsersCount = new AtomicInteger(0);

    @Autowired
    public MessageCreateListener(
            OpenAiChatClient chatClient,
            @Value("${discord.bot.answering-speed}") String discordBotAnsweringSpeed
    ) {
        this.chatClient = chatClient;
        this.discordBotAnsweringSpeed = discordBotAnsweringSpeed;
    }

    @Override
    public Class<MessageCreateEvent> getEventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> processEvent(MessageCreateEvent event) {
        return getMessageStartingWithEquals(event)
                .flatMapMany(this::processMessage)
                .then();
    }

    private Flux<Message> processMessage(Message message) {
        StringBuilder combinedStringBuilder = new StringBuilder();
        return sendEditableThinkingMessage(message)
                .flatMapMany(thinkingMessage ->
                        updateMessageContent(thinkingMessage, streamAndFilterChatMessages(message), combinedStringBuilder)
                )
                .doOnComplete(() -> logRetrievedCombinedResponse(combinedStringBuilder));
    }

    private Mono<Message> sendEditableThinkingMessage(Message message) {
        return message.getChannel()
                .flatMap(channel -> channel.createMessage("Думаю, что бы такого тебе ответить..."));
    }

    private Mono<Message> getMessageStartingWithEquals(MessageCreateEvent event) {
        return Mono.just(event.getMessage())
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(true))
                .filter(message -> message.getContent().startsWith("=="))
                .doOnNext(MessageCreateListener::logRetrievedRequest);
    }

    private static void logRetrievedRequest(Message message) {
        LOG.info("Request retrieved. Author: {}, Content: {}",
                message.getAuthor().map(User::getUsername).orElse("Hidden Username"),
                message.getContent()
        );
    }

    private void logRetrievedCombinedResponse(StringBuilder combinedStringBuilder) {
        if (!combinedStringBuilder.isEmpty()) {
            LOG.info("[{}] Combined response retrieved: Content: {}",
                    servedUsersCount.incrementAndGet(),
                    combinedStringBuilder
            );
        }
    }

    private Flux<List<String>> streamAndFilterChatMessages(Message message) {
        return chatClient.stream(message.getContent())
                .filter(partOfTheMessage -> !partOfTheMessage.isBlank())
                .buffer(Integer.parseInt(discordBotAnsweringSpeed));
    }

    private Flux<Message> updateMessageContent(
            Message message,
            Flux<List<String>> sendUserMessageToChatClientStream,
            StringBuilder combinedStringBuilder
    ) {
        return sendUserMessageToChatClientStream.flatMap(partOfTheMessage ->
                message.edit(
                        MessageEditSpec.create()
                                .withContent(Possible.of(
                                        Optional.of(combineStrings(partOfTheMessage, combinedStringBuilder)))
                                )
                )
        );
    }

    private String combineStrings(List<String> partOfTheMessage, StringBuilder combinedStringBuilder) {
        partOfTheMessage.forEach(combinedStringBuilder::append);
        return combinedStringBuilder.toString();
    }

}
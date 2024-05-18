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

    /**
     * Processes the event of a new message creation.
     * The method filters out messages from bots and messages that do not start with "==" prefix.
     * The method sends a message to the chat client and updates the message content with the response.
     * The method logs the retrieved request and the combined response.
     *
     * @param event the event of a new message creation
     * @return a {@link Mono} of the void type
     */
    @Override
    public Mono<Void> processEvent(MessageCreateEvent event) {
        return getMessageStartingWithEquals(event)
                .flatMapMany(this::processMessage)
                .then();
    }

    /**
     * Processes the message by sending an editable thinking message, updating the message content with the response,
     * and logging the combined response.
     *
     * @param message the message to process
     * @return a {@link Flux} of the {@link Message} type
     */
    private Flux<Message> processMessage(Message message) {
        StringBuilder combinedStringBuilder = new StringBuilder();
        return sendEditableThinkingMessage(message)
                .flatMapMany(thinkingMessage ->
                        updateMessageContent(thinkingMessage, streamAndFilterChatMessages(message), combinedStringBuilder)
                )
                .doOnComplete(() -> logRetrievedCombinedResponse(combinedStringBuilder));
    }

    /**
     * Sends an editable thinking message to the channel of the message.
     *
     * @param message the message to send the editable thinking message to
     * @return a {@link Mono} of the {@link Message} type
     */
    private Mono<Message> sendEditableThinkingMessage(Message message) {
        return message.getChannel()
                .flatMap(channel -> channel.createMessage("Думаю, что бы такого тебе ответить..."));
    }

    /**
     * Retrieves the message starting with the "==" prefix.
     *
     * @param event the event of a new message creation
     * @return a {@link Mono} of the {@link Message} type
     */
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

    /**
     * Streams and filters chat messages.
     * The method filters out blank messages and buffers the messages by the discord bot answering speed.
     *
     * @param message the message to stream and filter chat messages
     * @return a {@link Flux} of the {@link List} of the {@link String} type
     */
    private Flux<List<String>> streamAndFilterChatMessages(Message message) {
        return chatClient.stream(message.getContent())
                .filter(partOfTheMessage -> !partOfTheMessage.isBlank())
                .buffer(Integer.parseInt(discordBotAnsweringSpeed));
    }

    /**
     * The method sends the user message to the ChatGPT and updates the content with the response.
     *
     * @param message                           the message to update the content with the response
     * @param sendUserMessageToChatClientStream the stream to send the user message to the chat client
     * @param combinedStringBuilder             the combined string builder to update the content with the response
     * @return a {@link Flux} of the {@link Message} type
     */
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

    /**
     * Combines the strings.
     *
     * @param partOfTheMessage the part of the message to combine
     * @param combinedStringBuilder the combined string builder to combine the strings
     * @return the combined string
     */
    private String combineStrings(List<String> partOfTheMessage, StringBuilder combinedStringBuilder) {
        partOfTheMessage.forEach(combinedStringBuilder::append);
        return combinedStringBuilder.toString();
    }

}
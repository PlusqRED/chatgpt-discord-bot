package com.aleh.gpt.discord.app.listener;

import com.aleh.gpt.discord.app.client.ChatClient;
import com.aleh.gpt.discord.app.dao.cache.repository.RedisRepository;
import com.aleh.gpt.discord.app.dto.request.ChatRequest;
import com.aleh.gpt.discord.app.dto.request.Message;
import com.aleh.gpt.discord.app.dto.response.ChatCompletionChunk;
import com.aleh.gpt.discord.app.dto.response.Choice;
import com.aleh.gpt.discord.app.dto.response.Delta;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.possible.Possible;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MessageCreateListener implements EventListener<MessageCreateEvent> {

    private final ChatClient chatClient;
    private final RedisRepository redisRepository;
    private final AtomicInteger servedUsersCount = new AtomicInteger(0);

    @Value("${discord.bot.answering-speed}")
    private String discordBotAnsweringSpeed;

    @Value("${openai.active-model}")
    private String gptModel;

    @Value("${openai.memory-messages-count}")
    private String memoryMessagesCount;

    @Value("${openai.memory-messages-forget-count}")
    private String memoryMessagesForgetCount;

    @Value("${openai.assistant-preprompt}")
    private String assistantPrePrompt;


    public MessageCreateListener(ChatClient chatClient, RedisRepository redisRepository) {
        this.chatClient = chatClient;
        this.redisRepository = redisRepository;
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

    private static void logRetrievedRequest(discord4j.core.object.entity.Message message) {
        LOG.info("Request retrieved. Author: {}, Content: {}",
                message.getAuthor().map(User::getUsername).orElse("Hidden Username"),
                message.getContent()
        );
    }

    private Mono<Void> processMessage(discord4j.core.object.entity.Message message) {
        String userName = message.getAuthor().map(User::getUsername).map(un -> "[" + un + "] ").orElse("[Unknown] ");
        StringBuilder combinedStringBuilder = new StringBuilder(userName);

        return sendEditableThinkingMessage(message)
                .flatMapMany(thinkingMessage ->
                        updateMessageContent(thinkingMessage, streamChatMessages(message), combinedStringBuilder)
                )
                .doOnComplete(() -> logRetrievedCombinedResponse(combinedStringBuilder))
                .then(Mono.defer(() -> temporaryStoreMessage(getDiscordUserName(message), "assistant", combinedStringBuilder.toString())));
    }

    private Mono<Void> temporaryStoreMessage(String discordUserName, String role, String content) {
        return redisRepository.find(discordUserName)
                .collectList()
                .filter(history -> !history.isEmpty())
                .switchIfEmpty(Mono.just(new ArrayList<>(Collections.singletonList(new Message("system", assistantPrePrompt)))))
                .doOnNext(history -> history.add(new Message(role, content)))
                .map(this::trimHistoryIfBig)
                .flatMap(history -> redisRepository.flushAll(discordUserName)
                        .then(redisRepository.save(discordUserName, history))
                );
    }

    private String getDiscordUserName(discord4j.core.object.entity.Message message) {
        return message.getAuthor().map(User::getUsername).orElse("-=-Unknown-=-");
    }

    private List<Message> trimHistoryIfBig(List<Message> history) {
        if (history.size() > Integer.parseInt(memoryMessagesCount)) {
            return history.subList(Integer.parseInt(memoryMessagesForgetCount), history.size());
        } else {
            return history;
        }
    }

    private Mono<discord4j.core.object.entity.Message> sendEditableThinkingMessage(discord4j.core.object.entity.Message message) {
        return message.getChannel()
                .flatMap(channel -> channel.createMessage("Думаю, что бы такого тебе ответить..."));
    }

    private Mono<discord4j.core.object.entity.Message> getMessageStartingWithEquals(MessageCreateEvent event) {
        return Mono.just(event.getMessage())
                .filter(message -> message.getAuthor().map(user -> !user.isBot()).orElse(true))
                .filter(message -> message.getContent().startsWith("=="))
                .doOnNext(MessageCreateListener::logRetrievedRequest);
    }

    private void logRetrievedCombinedResponse(StringBuilder combinedStringBuilder) {
        if (!combinedStringBuilder.isEmpty()) {
            LOG.info("[{}] Combined response retrieved: Content: {}",
                    servedUsersCount.incrementAndGet(),
                    combinedStringBuilder
            );
        }
    }

    private Flux<List<ChatCompletionChunk>> streamChatMessages(discord4j.core.object.entity.Message message) {
        return temporaryStoreMessage(getDiscordUserName(message), "user", message.getContent())
                .thenMany(redisRepository.find(getDiscordUserName(message))
                        .collectList()
                        .filterWhen(history -> isHistorySizeValid(getDiscordUserName(message)))
                        .switchIfEmpty(eraseBeginningIfHistoryBig(message))
                        .flatMapMany(history -> chatClient.chatCompletions(new ChatRequest(gptModel, history, true))
                                .buffer(Integer.parseInt(discordBotAnsweringSpeed))));
    }

    private Mono<Boolean> isHistorySizeValid(String discordUserName) {
        return redisRepository.count(discordUserName).map(count -> count < Integer.parseInt(memoryMessagesCount));
    }

    private Mono<List<Message>> eraseBeginningIfHistoryBig(discord4j.core.object.entity.Message message) {
        return Mono.defer(() ->
                redisRepository.flushFromBeginning(Integer.parseInt(memoryMessagesForgetCount), getDiscordUserName(message))
                        .thenMany(redisRepository.find(getDiscordUserName(message)))
                        .collectList());
    }

    private Flux<discord4j.core.object.entity.Message> updateMessageContent(
            discord4j.core.object.entity.Message message,
            Flux<List<ChatCompletionChunk>> sendUserMessageToChatClientStream,
            StringBuilder combinedStringBuilder
    ) {
        return sendUserMessageToChatClientStream.flatMap(chatCompletionChunks ->
                message.edit(
                        MessageEditSpec.create()
                                .withContent(Possible.of(
                                        Optional.of(buildCombinedString(chatCompletionChunks, combinedStringBuilder)))
                                )
                )
        );
    }

    private String buildCombinedString(List<ChatCompletionChunk> messageParts, StringBuilder combinedStringBuilder) {
        messageParts.stream()
                .map(ChatCompletionChunk::choices)
                .map(List::getFirst)
                .map(Choice::delta)
                .map(Delta::content)
                .forEach(combinedStringBuilder::append);

        return combinedStringBuilder.toString();
    }

}
package com.aleh.gpt.discord.app.dao.cache.repository;

import com.aleh.gpt.discord.app.dto.request.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface RedisRepository {
    Mono<Void> save(String discordUserName, List<Message> history);

    Flux<Message> find(String userName);

    Mono<Void> flushFromBeginning(int amount, String userName);

    Mono<Void> flushAll(String userName);

    Mono<Integer> count(String userName);
}

package com.aleh.gpt.discord.app.dao.cache.repository;

import com.aleh.gpt.discord.app.dto.request.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Repository
public class DefaultRedisRepository implements RedisRepository {

    private final ReactiveStringRedisTemplate redisTemplate;
    @Value("${openai.memory-duration-minutes}")
    private String memoryDurationMinutes;

    public DefaultRedisRepository(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> save(String discordUserName, List<Message> history) {
        return redisTemplate.opsForList()
                .rightPushAll(discordUserName, history.stream()
                        .map(Message::toJson)
                        .toList())
                .then(Mono.defer(() -> redisTemplate.expire(discordUserName, Duration.ofMinutes(Integer.parseInt(memoryDurationMinutes)))))
                .then();
    }

    @Override
    public Flux<Message> find(String userName) {
        return redisTemplate.opsForList()
                .range(userName, 0, -1)
                .map(Message::fromJson);
    }

    @Override
    public Mono<Void> flushFromBeginning(int amount, String userName) {
        return redisTemplate.opsForList()
                .trim(userName, amount, -1)
                .then();
    }

    @Override
    public Mono<Void> flushAll(String userName) {
        return redisTemplate.delete(userName)
                .then();
    }

    @Override
    public Mono<Integer> count(String userName) {
        return redisTemplate.opsForList()
                .size(userName)
                .map(Long::intValue);
    }
}

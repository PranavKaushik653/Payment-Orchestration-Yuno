package com.yuno.payments.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyStore {

    private static final String KEY_PREFIX = "idempotency:payment:";

    private final StringRedisTemplate redisTemplate;

    @Value("${payment.idempotency.ttl-seconds:86400}")
    private long ttlSeconds;

    /**
     * Stores a mapping of idempotencyKey → paymentId in Redis.
     *
     * @param idempotencyKey the client-provided key (from X-Idempotency-Key header)
     * @param paymentId      the UUID of the newly created payment
     */
    public void store(String idempotencyKey, String paymentId) {
        String redisKey = buildKey(idempotencyKey);
        redisTemplate.opsForValue().set(redisKey, paymentId, Duration.ofSeconds(ttlSeconds));
        log.debug("Stored idempotency key={} → paymentId={} (TTL={}s)", idempotencyKey, paymentId, ttlSeconds);
    }

    public Optional<String> findExistingPaymentId(String idempotencyKey) {
        String redisKey = buildKey(idempotencyKey);
        String existingId = redisTemplate.opsForValue().get(redisKey);
        return Optional.ofNullable(existingId);
    }

    public boolean exists(String idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(idempotencyKey)));
    }

    public void remove(String idempotencyKey) {
        redisTemplate.delete(buildKey(idempotencyKey));
    }

    private String buildKey(String idempotencyKey) {

        return KEY_PREFIX + idempotencyKey;
    }
}
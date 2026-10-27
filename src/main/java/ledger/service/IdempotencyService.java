package ledger.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class IdempotencyService {
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration LOCK_TTL = Duration.ofSeconds(60);
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public enum IdempotencyStatus {
        ACQUIRED,   // New request, lock acquired
        IN_PROGRESS, // duplicate, reject with 409
        COMPLETED // previously executed, return the cached response
    }

    public record IdempotencyResult(IdempotencyStatus status, String cachedResponse) {}

    // lock the key in Redis using SET NX
    public IdempotencyResult tryAcquire(String idempotencyKey) {
        String redisKey = "idempotency:" + idempotencyKey;

        // Atomic set if not exists with 60s TTL
        Boolean setSuccess = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, LOCK_TTL);

        if (Boolean.TRUE.equals(setSuccess)) {
            return new IdempotencyResult(IdempotencyStatus.ACQUIRED, null);
        }

        // key exist, check current state
        String existingValue = redisTemplate.opsForValue().get(redisKey);

        if (IN_PROGRESS.equals(existingValue)) {
            return new IdempotencyResult(IdempotencyStatus.IN_PROGRESS, null);
        }

        return new IdempotencyResult(IdempotencyStatus.COMPLETED, existingValue);
    }

    // cache final response after tx completes
    public void saveResponse(String idempotencyKey, String responseJson) {
        String redisKey = "idempotency:" + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, responseJson, RESPONSE_TTL);
    }

    // release lock if tx fails so client can retry
    public void releaseLock(String idempotencyKey) {
        String redisKey = "idempotency:" + idempotencyKey;
        redisTemplate.delete(redisKey);
    }

}

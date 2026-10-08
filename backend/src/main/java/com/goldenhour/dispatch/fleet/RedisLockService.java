package com.goldenhour.dispatch.fleet;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Distributed lock on Redis, so that several dispatcher threads (or several app instances) never work on
 * the same ambulance at the same time.
 *
 * <ul>
 *   <li><b>Acquire:</b> {@code SET key token NX PX ttl} - only succeeds if nobody holds the key; the TTL
 *       frees the lock automatically if the holder crashes.</li>
 *   <li><b>Release:</b> a Lua script deletes the key only if it still holds <i>our</i> token. Without the
 *       check, a slow holder whose lock already expired could delete someone else's lock.</li>
 * </ul>
 * The lock is the fast path; the database compare-and-set in {@link AmbulanceRepository#claim} is the
 * final guarantee (like a fencing check), so even a Redis failure cannot cause a double booking.
 */
@Service
public class RedisLockService {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<String> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent("lock:" + key, token, ttl);
        return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
    }

    public boolean unlock(String key, String token) {
        Long deleted = redis.execute(RELEASE, List.of("lock:" + key), token);
        return deleted != null && deleted > 0;
    }
}

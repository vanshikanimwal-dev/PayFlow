package com.payflow.common;

import com.payflow.config.PayflowProperties;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            local refill = capacity / window_ms
            local delta = math.max(0, now - ts)
            tokens = math.min(capacity, tokens + (delta * refill))
            local allowed = 0
            local retry_seconds = 1
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              local missing = 1 - tokens
              retry_seconds = math.ceil(missing / refill / 1000)
              if retry_seconds < 1 then retry_seconds = 1 end
            end
            redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, window_ms * 2)
            return {allowed, retry_seconds}
            """;

    private final StringRedisTemplate redis;
    private final PayflowProperties properties;
    private final DefaultRedisScript<List> script;

    public RateLimiter(StringRedisTemplate redis, PayflowProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(SCRIPT);
        this.script.setResultType(List.class);
    }

    public void login(String ip, String email) {
        hit("login:" + ip + ":" + email.toLowerCase(), properties.getRateLimit().getLoginPerMinute(), 60);
    }

    public void money(java.util.UUID userId) {
        hit("money:" + userId, properties.getRateLimit().getMoneyPerMinute(), 60);
    }

    public void hit(String bucket, int capacity, int windowSeconds) {
        long now = System.currentTimeMillis();
        List<?> result = redis.execute(
                script,
                List.of("rl:" + bucket),
                Integer.toString(capacity),
                Integer.toString(windowSeconds * 1000),
                Long.toString(now));
        if (result == null || result.size() < 2) {
            throw new PayflowException(ErrorCode.INTERNAL_ERROR, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred");
        }
        long allowed = ((Number) result.get(0)).longValue();
        long retry = ((Number) result.get(1)).longValue();
        if (allowed != 1) {
            throw new RateLimitedException(retry);
        }
    }
}

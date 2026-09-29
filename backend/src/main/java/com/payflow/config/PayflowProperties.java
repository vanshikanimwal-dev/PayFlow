package com.payflow.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow")
public class PayflowProperties {

    private final Jwt jwt = new Jwt();
    private final Gateway gateway = new Gateway();
    private final Locking locking = new Locking();
    private final Limits limits = new Limits();
    private final Fees fees = new Fees();
    private final Payments payments = new Payments();
    private final Idempotency idempotency = new Idempotency();
    private final Saga saga = new Saga();
    private final RateLimit rateLimit = new RateLimit();
    private final Outbox outbox = new Outbox();
    private final Jobs jobs = new Jobs();
    private final Cors cors = new Cors();

    public Jwt getJwt() {
        return jwt;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public Locking getLocking() {
        return locking;
    }

    public Limits getLimits() {
        return limits;
    }

    public Fees getFees() {
        return fees;
    }

    public Payments getPayments() {
        return payments;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public Saga getSaga() {
        return saga;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Jobs getJobs() {
        return jobs;
    }

    public Cors getCors() {
        return cors;
    }

    public static class Jwt {
        private String secret = "dev-only-secret-change-me-please-32b";
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(7);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTtl() {
            return accessTtl;
        }

        public void setAccessTtl(Duration accessTtl) {
            this.accessTtl = accessTtl;
        }

        public Duration getRefreshTtl() {
            return refreshTtl;
        }

        public void setRefreshTtl(Duration refreshTtl) {
            this.refreshTtl = refreshTtl;
        }
    }

    public static class Gateway {
        private String baseUrl = "http://localhost:8081";
        private String callbackUrl = "http://localhost:8080/api/v1/webhooks/gateway";
        private String hmacSecret = "dev-gateway-hmac-secret";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(3);

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public void setCallbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
        }

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Locking {
        private String strategy = "pessimistic";
        private Duration lockTimeout = Duration.ofSeconds(3);

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public boolean optimistic() {
            return "optimistic".equalsIgnoreCase(strategy);
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        public String lockTimeoutSql() {
            long seconds = Math.max(1, lockTimeout.toSeconds());
            return seconds + "s";
        }
    }

    public static class Limits {
        private long maxTransferMinor = 5_000_000L;
        private long dailyTransferMinor = 10_000_000L;

        public long getMaxTransferMinor() {
            return maxTransferMinor;
        }

        public void setMaxTransferMinor(long maxTransferMinor) {
            this.maxTransferMinor = maxTransferMinor;
        }

        public long getDailyTransferMinor() {
            return dailyTransferMinor;
        }

        public void setDailyTransferMinor(long dailyTransferMinor) {
            this.dailyTransferMinor = dailyTransferMinor;
        }
    }

    public static class Fees {
        private int paymentPercent = 2;

        public int getPaymentPercent() {
            return paymentPercent;
        }

        public void setPaymentPercent(int paymentPercent) {
            this.paymentPercent = paymentPercent;
        }
    }

    public static class Payments {
        private Duration requestTtl = Duration.ofMinutes(15);

        public Duration getRequestTtl() {
            return requestTtl;
        }

        public void setRequestTtl(Duration requestTtl) {
            this.requestTtl = requestTtl;
        }
    }

    public static class Idempotency {
        private Duration ttl = Duration.ofHours(24);
        private Duration inProgressRecoverAfter = Duration.ofMinutes(2);

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getInProgressRecoverAfter() {
            return inProgressRecoverAfter;
        }

        public void setInProgressRecoverAfter(Duration inProgressRecoverAfter) {
            this.inProgressRecoverAfter = inProgressRecoverAfter;
        }
    }

    public static class Saga {
        private Duration staleAfter = Duration.ofSeconds(60);
        private Duration giveUpAfter = Duration.ofHours(24);

        public Duration getStaleAfter() {
            return staleAfter;
        }

        public void setStaleAfter(Duration staleAfter) {
            this.staleAfter = staleAfter;
        }

        public Duration getGiveUpAfter() {
            return giveUpAfter;
        }

        public void setGiveUpAfter(Duration giveUpAfter) {
            this.giveUpAfter = giveUpAfter;
        }
    }

    public static class RateLimit {
        private int loginPerMinute = 10;
        private int moneyPerMinute = 30;

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getMoneyPerMinute() {
            return moneyPerMinute;
        }

        public void setMoneyPerMinute(int moneyPerMinute) {
            this.moneyPerMinute = moneyPerMinute;
        }
    }

    public static class Outbox {
        private String broker = "in-process";

        public String getBroker() {
            return broker;
        }

        public void setBroker(String broker) {
            this.broker = broker;
        }

        public boolean rabbit() {
            return "rabbit".equalsIgnoreCase(broker);
        }
    }

    public static class Jobs {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Cors {
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }
    }
}

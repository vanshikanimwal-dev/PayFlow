package com.payflow.auth;

import com.payflow.config.PayflowProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(PayflowProperties properties, Clock clock) {
        byte[] secret = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = properties.getJwt().getAccessTtl();
        this.clock = clock;
    }

    public long expiresInSeconds() {
        return accessTtl.toSeconds();
    }

    public String accessToken(AppUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                UserRole.valueOf(claims.get("role", String.class)));
    }
}

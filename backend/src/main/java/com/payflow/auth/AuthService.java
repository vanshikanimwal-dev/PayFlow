package com.payflow.auth;

import com.payflow.account.Account;
import com.payflow.account.AccountRepository;
import com.payflow.account.AccountStatus;
import com.payflow.account.AccountType;
import com.payflow.audit.AuditService;
import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.Hashes;
import com.payflow.common.PayflowException;
import com.payflow.common.RateLimiter;
import com.payflow.config.PayflowProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final IdGenerator ids;
    private final Clock clock;
    private final PayflowProperties properties;
    private final RateLimiter rateLimiter;
    private final AuditService audit;

    public AuthService(
            UserRepository users,
            AccountRepository accounts,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwords,
            JwtService jwt,
            IdGenerator ids,
            Clock clock,
            PayflowProperties properties,
            RateLimiter rateLimiter,
            AuditService audit) {
        this.users = users;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.ids = ids;
        this.clock = clock;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Admin registration is not allowed");
        }
        String email = request.email().trim().toLowerCase();
        String phone = blankToNull(request.phone());
        if (users.findByEmail(email).isPresent()) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.CONFLICT, "Email is already registered");
        }
        if (phone != null && users.findByPhone(phone).isPresent()) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.CONFLICT, "Phone is already registered");
        }
        Instant now = clock.instant();
        AppUser user = new AppUser();
        user.setId(ids.newId());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        users.save(user);

        Account account = new Account();
        account.setId(ids.newId());
        account.setOwnerId(user.getId());
        account.setType(request.role() == UserRole.MERCHANT ? AccountType.MERCHANT : AccountType.USER_WALLET);
        account.setCurrency("INR");
        account.setBalanceMinor(0);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCreatedAt(now);
        accounts.save(account);

        audit.record(user.getId(), user.getRole().name(), "REGISTER", "user", user.getId().toString(), null,
                Map.of("email", email, "role", user.getRole().name()));
        return issue(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        rateLimiter.login(ClientIp.current(), email);
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            audit.record(user == null ? null : user.getId(), user == null ? null : user.getRole().name(),
                    "LOGIN_FAILED", "user", user == null ? email : user.getId().toString(), null, Map.of("result", "failure"));
            throw new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new PayflowException(ErrorCode.ACCOUNT_INACTIVE, HttpStatus.UNPROCESSABLE_ENTITY, "Account is not active");
        }
        audit.record(user.getId(), user.getRole().name(), "LOGIN", "user", user.getId().toString(), null, Map.of("result", "success"));
        return issue(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(String rawToken) {
        String hash = Hashes.sha256(rawToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash).orElseThrow(this::unauthenticated);
        if (stored.isRevoked()) {
            refreshTokens.revokeAllForUser(stored.getUserId());
            throw unauthenticated();
        }
        if (stored.getExpiresAt().isBefore(clock.instant())) {
            stored.setRevoked(true);
            throw new PayflowException(ErrorCode.TOKEN_EXPIRED, HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }
        stored.setRevoked(true);
        AppUser user = users.findById(stored.getUserId()).orElseThrow(this::unauthenticated);
        return issue(user);
    }

    @Transactional
    public void logout(UUID userId, String rawToken) {
        String hash = Hashes.sha256(rawToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash).orElseThrow(this::unauthenticated);
        if (!stored.getUserId().equals(userId)) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You do not have access to this resource");
        }
        stored.setRevoked(true);
    }

    private AuthDtos.AuthResponse issue(AppUser user) {
        String raw = ids.newId() + "." + ids.newId();
        RefreshToken token = new RefreshToken();
        token.setId(ids.newId());
        token.setUserId(user.getId());
        token.setTokenHash(Hashes.sha256(raw));
        token.setExpiresAt(clock.instant().plus(properties.getJwt().getRefreshTtl()));
        token.setRevoked(false);
        refreshTokens.save(token);
        return new AuthDtos.AuthResponse(jwt.accessToken(user), raw, jwt.expiresInSeconds());
    }

    private PayflowException unauthenticated() {
        return new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

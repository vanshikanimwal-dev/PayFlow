package com.payflow.notify;

import com.payflow.account.Account;
import com.payflow.account.SavingsService;
import com.payflow.auth.PinGuard;
import com.payflow.account.WalletLookup;
import com.payflow.auth.AppUser;
import com.payflow.auth.CurrentUser;
import com.payflow.auth.RefreshToken;
import com.payflow.auth.RefreshTokenRepository;
import com.payflow.auth.Totp;
import com.payflow.auth.UserRepository;
import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.PayflowException;
import com.payflow.common.RequestHasher;
import com.payflow.request.MoneyRequest;
import com.payflow.request.MoneyRequestService;
import com.payflow.schedule.ScheduledTransfer;
import com.payflow.schedule.ScheduledTransferRepository;
import com.payflow.transfer.TransferDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ControlsController {

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenRepository sessions;
    private final WalletLookup wallets;
    private final SavingsService savings;
    private final NotificationRepository notifications;
    private final ScheduledTransferRepository schedules;
    private final MoneyRequestService moneyRequests;
    private final IdGenerator ids;
    private final Clock clock;
    private final RequestHasher hasher;
    private final PinGuard pins;

    public ControlsController(
            CurrentUser currentUser,
            UserRepository users,
            PasswordEncoder passwords,
            RefreshTokenRepository sessions,
            WalletLookup wallets,
            SavingsService savings,
            NotificationRepository notifications,
            ScheduledTransferRepository schedules,
            MoneyRequestService moneyRequests,
            IdGenerator ids,
            Clock clock,
            RequestHasher hasher,
            PinGuard pins) {
        this.currentUser = currentUser;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.wallets = wallets;
        this.savings = savings;
        this.notifications = notifications;
        this.schedules = schedules;
        this.moneyRequests = moneyRequests;
        this.ids = ids;
        this.clock = clock;
        this.hasher = hasher;
        this.pins = pins;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ControlsDtos.Profile me() {
        AppUser user = load();
        Long savingsMinor = wallets.savings(user.getId()).map(Account::getBalanceMinor).orElse(null);
        return new ControlsDtos.Profile(
                user.getEmail(),
                user.getDisplayName(),
                user.isWalletLocked(),
                user.getPinHash() != null,
                user.isTotpEnabled(),
                savingsMinor);
    }

    @PostMapping("/auth/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void setPin(@Valid @RequestBody ControlsDtos.PinRequest request) {
        AppUser user = load();
        user.setPinHash(passwords.encode(request.pin()));
    }

    @PostMapping("/auth/2fa/setup")
    @Transactional
    public ControlsDtos.TotpSetup setupTotp() {
        AppUser user = load();
        String secret = Totp.randomSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);
        String uri = "otpauth://totp/PayFlow:" + user.getEmail() + "?secret=" + secret + "&issuer=PayFlow";
        return new ControlsDtos.TotpSetup(secret, uri);
    }

    @PostMapping("/auth/2fa/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void confirmTotp(@Valid @RequestBody ControlsDtos.CodeRequest request) {
        AppUser user = load();
        if (user.getTotpSecret() == null || !Totp.matches(user.getTotpSecret(), request.code(), clock.instant())) {
            throw new PayflowException(ErrorCode.TWO_FACTOR_REQUIRED, HttpStatus.UNPROCESSABLE_ENTITY, "Authenticator code is incorrect");
        }
        user.setTotpEnabled(true);
    }

    @GetMapping("/auth/sessions")
    @Transactional(readOnly = true)
    public List<ControlsDtos.SessionView> sessions() {
        UUID userId = currentUser.require().id();
        return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(token -> new ControlsDtos.SessionView(token.getId(), token.getDeviceLabel(), token.getCreatedAt(), token.isRevoked()))
                .toList();
    }

    @PostMapping("/auth/sessions/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void revoke(@PathVariable UUID id) {
        UUID userId = currentUser.require().id();
        RefreshToken token = sessions.findById(id)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Session not found"));
        if (!token.getUserId().equals(userId)) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You do not have access to this resource");
        }
        token.setRevoked(true);
    }

    @PostMapping("/wallet/lock")
    @Transactional
    public ControlsDtos.Profile lock(@RequestBody ControlsDtos.LockRequest request) {
        AppUser user = load();
        user.setWalletLocked(request.locked());
        return me();
    }

    @PostMapping("/wallet/savings")
    @ResponseStatus(HttpStatus.CREATED)
    public ControlsDtos.Profile openSavings() {
        savings.open(currentUser.require().id());
        return me();
    }

    @PostMapping("/wallet/savings/move")
    @ResponseStatus(HttpStatus.CREATED)
    public SavingsService.MoveResponse move(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ControlsDtos.MoveRequest request,
            HttpServletRequest http) {
        var user = currentUser.require();
        pins.require(user.id(), http.getHeader("X-Transaction-Pin"));
        return savings.move(user.id(), key, hasher.hash("POST", http.getRequestURI(), request), request.amountMinor(), request.toSavings());
    }

    @GetMapping("/notifications")
    @Transactional(readOnly = true)
    public List<ControlsDtos.NotificationView> notifications() {
        return notifications.findTop50ByUserIdOrderByCreatedAtDesc(currentUser.require().id()).stream()
                .map(row -> new ControlsDtos.NotificationView(row.getId(), row.getTitle(), row.getBody(), row.isSeen(), row.getCreatedAt()))
                .toList();
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void read(@PathVariable UUID id) {
        AppNotification row = notifications.findById(id)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Notification not found"));
        if (!row.getUserId().equals(currentUser.require().id())) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You do not have access to this resource");
        }
        row.setSeen(true);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ControlsDtos.ScheduleView schedule(@Valid @RequestBody ControlsDtos.ScheduleRequest request) {
        UUID userId = currentUser.require().id();
        String email = request.toEmail().trim().toLowerCase();
        users.findByEmail(email)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Recipient not found"));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate next = today.getDayOfMonth() < request.dayOfMonth()
                ? today.withDayOfMonth(request.dayOfMonth())
                : today.withDayOfMonth(1).plusMonths(1).withDayOfMonth(request.dayOfMonth());
        ScheduledTransfer row = new ScheduledTransfer();
        row.setId(ids.newId());
        row.setUserId(userId);
        row.setToEmail(email);
        row.setAmountMinor(request.amountMinor());
        row.setNote(request.note());
        row.setDayOfMonth(request.dayOfMonth());
        row.setNextRunOn(next);
        row.setActive(true);
        schedules.save(row);
        return view(row);
    }

    @GetMapping("/schedules")
    @Transactional(readOnly = true)
    public List<ControlsDtos.ScheduleView> schedules() {
        return schedules.findByUserIdOrderByNextRunOnAsc(currentUser.require().id()).stream().map(this::view).toList();
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ControlsDtos.RequestView> requestMoney(@Valid @RequestBody ControlsDtos.SplitRequest request) {
        AppUser user = load();
        List<MoneyRequestService.Share> shares = request.shares().stream()
                .map(line -> new MoneyRequestService.Share(line.email(), line.amountMinor()))
                .toList();
        return moneyRequests.create(user.getId(), request.note(), shares).stream()
                .map(row -> view(row, user.getEmail()))
                .toList();
    }

    @GetMapping("/requests")
    public List<ControlsDtos.RequestView> requests() {
        AppUser user = load();
        return moneyRequests.list(user.getId(), user.getEmail()).stream().map(row -> view(row, user.getEmail())).toList();
    }

    @PostMapping("/requests/{id}/pay")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferDtos.TransferResponse payRequest(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest http) {
        var user = currentUser.require();
        pins.require(user.id(), http.getHeader("X-Transaction-Pin"));
        String hash = hasher.hash("POST", http.getRequestURI(), id.toString());
        return moneyRequests.pay(user.id(), id, key, hash);
    }

    private AppUser load() {
        return users.findById(currentUser.require().id())
                .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required"));
    }

    private ControlsDtos.ScheduleView view(ScheduledTransfer row) {
        return new ControlsDtos.ScheduleView(
                row.getId(), row.getToEmail(), row.getAmountMinor(), row.getNote(), row.getDayOfMonth(), row.getNextRunOn(), row.isActive());
    }

    private ControlsDtos.RequestView view(MoneyRequest row, String email) {
        return new ControlsDtos.RequestView(
                row.getId(),
                row.getPayerEmail(),
                row.getAmountMinor(),
                row.getNote(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getPayerEmail().equalsIgnoreCase(email));
    }
}

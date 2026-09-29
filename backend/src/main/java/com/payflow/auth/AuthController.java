package com.payflow.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser currentUser;

    public AuthController(AuthService auth, CurrentUser currentUser) {
        this.auth = auth;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        auth.logout(currentUser.require().id(), request.refreshToken());
    }
}

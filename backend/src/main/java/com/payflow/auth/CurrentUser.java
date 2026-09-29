package com.payflow.auth;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public UserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal;
    }
}

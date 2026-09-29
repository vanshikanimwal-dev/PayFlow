package com.payflow.auth;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientIp {

    private ClientIp() {
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return "unknown";
    }
}

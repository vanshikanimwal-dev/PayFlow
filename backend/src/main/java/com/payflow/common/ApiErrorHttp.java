package com.payflow.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorHttp {

    private final ObjectMapper mapper;

    public ApiErrorHttp(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, ErrorCode code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(code.name(), message, CorrelationIds.current(), Instant.now()));
    }
}

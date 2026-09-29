package com.payflow.common;

import com.payflow.transaction.IllegalStateTransitionException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed";
        }
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        ErrorCode code = switch (status) {
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case UNAUTHORIZED -> ErrorCode.UNAUTHENTICATED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMITED;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION_ERROR : ErrorCode.INTERNAL_ERROR;
        };
        String message = switch (status) {
            case NOT_FOUND -> "Resource not found";
            case UNAUTHORIZED -> "Authentication is required";
            case FORBIDDEN -> "You do not have access to this resource";
            default -> {
                if (ex instanceof HttpMessageNotReadableException) {
                    yield "Request body is invalid";
                }
                yield status.is5xxServerError()
                        ? "An unexpected error occurred"
                        : "Request could not be processed";
            }
        };
        return error(status, code, message);
    }

    @ExceptionHandler(PayflowException.class)
    ResponseEntity<Object> handlePayflow(PayflowException ex) {
        if (ex.status().is5xxServerError()) {
            log.error("Payflow error {}", ex.code(), ex);
        }
        ResponseEntity<Object> response = error(ex.status(), ex.code(), ex.getMessage());
        if (ex instanceof RateLimitedException limited) {
            return ResponseEntity.status(ex.status())
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(limited.retryAfterSeconds()))
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Object> handleMissingHeader(MissingRequestHeaderException ex) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, ex.getHeaderName() + " is required");
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ResponseEntity<Object> handleIllegalTransition(IllegalStateTransitionException ex) {
        log.error("Illegal state transition", ex);
        return error(HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR, "Illegal transaction state transition");
    }

    @ExceptionHandler({CannotAcquireLockException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Object> handleLock(Exception ex) {
        log.warn("Account lock failed", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.LOCK_TIMEOUT, "Could not lock accounts; retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("non_negative_user")) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INSUFFICIENT_BALANCE, "Wallet balance is too low");
        }
        log.warn("Constraint violation", ex);
        return error(HttpStatus.CONFLICT, ErrorCode.VALIDATION_ERROR, "Request conflicts with existing data");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Request validation failed";
        }
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Object> error(HttpStatusCode status, ErrorCode code, String message) {
        ApiError body = new ApiError(code.name(), message, CorrelationIds.current(), Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}

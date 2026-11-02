package com.rightcapital.notification.api.controller.notification;

import com.rightcapital.notification.api.application.notification.IdempotencyConflictException;
import com.rightcapital.notification.api.application.notification.MissingIdempotencyKeyException;
import com.rightcapital.notification.api.application.notification.NotificationAcceptanceException;
import com.rightcapital.notification.api.application.notification.NotificationAuthenticationException;
import com.rightcapital.notification.api.application.notification.NotificationConfigurationException;
import com.rightcapital.notification.api.application.notification.NotificationMessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
class NotificationApiExceptionHandler {

    @ExceptionHandler(NotificationAuthenticationException.class)
    ResponseEntity<NotificationApiError> unauthorized(NotificationAuthenticationException exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(NotificationConfigurationException.class)
    ResponseEntity<NotificationApiError> unprocessable(NotificationConfigurationException exception, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<NotificationApiError> conflict(IdempotencyConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler({MissingIdempotencyKeyException.class, IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<NotificationApiError> badRequest(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler({NotificationAcceptanceException.class, NotificationMessagingException.class})
    ResponseEntity<NotificationApiError> unavailable(NotificationAcceptanceException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<NotificationApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(NotificationApiExceptionHandler::format)
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<NotificationApiError> unreadableRequest(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Request body is invalid", request);
    }

    private static String format(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    private static ResponseEntity<NotificationApiError> error(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new NotificationApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()
        ));
    }
}

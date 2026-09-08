package com.resistance.mvc.api;

import com.resistance.mvc.assistant.AssistantDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One error shape for every /api endpoint, so the React client has a
 * single thing to parse: {"error": "<code>"} plus, for validation, a
 * "fields" map of field -> message. Only the api package is covered; the
 * Thymeleaf controllers keep Spring's default error page.
 */
@RestControllerAdvice(basePackageClasses = ApiErrorHandler.class)
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "validation", "fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        // malformed JSON, or a value that cannot become the declared type
        // (an unknown status name, a date that is not yyyy-mm-dd)
        return ResponseEntity.badRequest().body(Map.of("error", "bad_request"));
    }

    @ExceptionHandler({NotFoundException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
        // the services throw IllegalArgumentException for "not yours"; a
        // foreign row and a missing row must look identical from outside
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, Object>> unauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthenticated"));
    }

    @ExceptionHandler(AssistantDisabledException.class)
    public ResponseEntity<Map<String, Object>> assistantDisabled(AssistantDisabledException e) {
        // no API key configured: the feature is off, not broken
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "assistant_disabled"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> internal(Exception e) {
        log.error("Unhandled API error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal"));
    }
}

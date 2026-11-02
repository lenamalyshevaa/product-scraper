package com.example.priceparser.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.priceparser.service.NotProductException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Все ошибки отдаются в одном формате: timestamp, status, error, message.
 */
@RestControllerAdvice
public class ErrorHandler {

    // MethodArgumentNotValidException (тело запроса) - наследник BindException (параметры /answer)
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message;
        if (fieldError == null) {
            message = "Неверные параметры запроса";
        } else if (fieldError.isBindingFailure()) {
            // например minPrice=abc
            message = "Неверное значение параметра " + fieldError.getField() + ": " + fieldError.getRejectedValue();
        } else {
            message = fieldError.getDefaultMessage();
        }
        return buildError(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return buildError(HttpStatus.BAD_REQUEST, "Не указан параметр " + e.getParameterName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException e) {
        return buildError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotProductException.class)
    public ResponseEntity<Map<String, Object>> handleNotProduct(NotProductException e) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        return buildError(e.getStatusCode(), e.getReason());
    }

    @ExceptionHandler({WebClientException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleLoadError(RuntimeException e) {
        return buildError(HttpStatus.BAD_GATEWAY, "Не удалось загрузить страницу: " + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatusCode status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        body.put("error", httpStatus != null ? httpStatus.getReasonPhrase() : "");
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

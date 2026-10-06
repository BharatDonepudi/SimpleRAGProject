package com.llmusingapi.backend.web;

import com.llmusingapi.backend.chat.AnswerUnavailableException;
import com.llmusingapi.backend.chat.ConversationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps exceptions to the status codes in Contract A. Every body is {@code {"error": "..."}}.
 * Messages never include stack traces or internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ConversationNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ErrorResponse.of("Conversation not found."));
    }

    @ExceptionHandler(AnswerUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAnswerUnavailable(AnswerUnavailableException ex) {
        return switch (ex.getReason()) {
            case TIMEOUT -> body(HttpStatus.GATEWAY_TIMEOUT, new ErrorResponse(
                    "The document assistant took too long to answer. Try again shortly.",
                    ex.getConversationId()));
            case UNAVAILABLE -> body(HttpStatus.SERVICE_UNAVAILABLE, new ErrorResponse(
                    "The document assistant is not ready yet. Try again shortly.",
                    ex.getConversationId()));
            case FAILED -> body(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse(
                    "The document assistant could not answer. Try again shortly.",
                    ex.getConversationId()));
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + Objects.requireNonNullElse(e.getDefaultMessage(), "is invalid"))
                .sorted()
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, ErrorResponse.of("Invalid request: " + details));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorResponse.of("Invalid request."));
    }

    /** Unreadable JSON, a missing body, or a field with the wrong type (for example a malformed conversationId). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorResponse.of("Malformed request body."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorResponse.of("Invalid value for parameter '" + ex.getName() + "'."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorResponse.of("Content-Type must be application/json."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of("Method " + ex.getMethod() + " is not supported for this path."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ErrorResponse.of("Not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ErrorResponse.of("Internal server error."));
    }

    private static ResponseEntity<ErrorResponse> body(HttpStatus status, ErrorResponse error) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(error);
    }
}

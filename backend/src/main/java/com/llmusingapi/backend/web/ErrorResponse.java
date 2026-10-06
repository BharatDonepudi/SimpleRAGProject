package com.llmusingapi.backend.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Error body for every error response (Contract A): {@code {"error": "..."}}.
 * {@code conversationId} is added only on 503/504 once a conversation exists.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, UUID conversationId) {

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, null);
    }
}

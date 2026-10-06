package com.llmusingapi.backend.chat;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

/** Body of a 200 response from {@code POST /api/chat}. {@code createdAt} is the assistant message time. */
public record ChatResponse(
        UUID conversationId,
        String answer,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ApiTimestamps.PATTERN, timezone = "UTC")
        Instant createdAt) {
}

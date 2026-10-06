package com.llmusingapi.backend.chat;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/** One entry of {@code GET /api/conversations/{id}/messages}. {@code role} is "user" or "assistant". */
public record MessageResponse(
        String role,
        String content,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ApiTimestamps.PATTERN, timezone = "UTC")
        Instant createdAt) {
}

package com.llmusingapi.backend.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body of {@code POST /api/chat}.
 *
 * <p>The message is trimmed in the compact constructor, which runs during JSON
 * binding and before Bean Validation, so the 1-2000 character rule applies to
 * the trimmed text as Contract A requires.
 */
public record ChatRequest(
        UUID conversationId,

        @NotBlank(message = "must not be blank")
        @Size(max = 2000, message = "must be at most 2000 characters after trimming")
        String message) {

    public ChatRequest {
        if (message != null) {
            message = message.strip();
        }
    }
}

package com.llmusingapi.backend.chat;

import java.util.UUID;

/**
 * The conversation exists and the user message was saved, but no answer was stored.
 * Carries the conversation id so the 503/504/500 body can include it (Contract A).
 */
public class AnswerUnavailableException extends RuntimeException {

    public enum Reason {
        /** rag-service unreachable or index still loading: 503. */
        UNAVAILABLE,
        /** rag-service did not answer within the read timeout: 504. */
        TIMEOUT,
        /** rag-service returned an error, or something else unexpected went wrong: 500. */
        FAILED
    }

    private final UUID conversationId;
    private final Reason reason;

    public AnswerUnavailableException(UUID conversationId, Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.conversationId = conversationId;
        this.reason = reason;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public Reason getReason() {
        return reason;
    }
}

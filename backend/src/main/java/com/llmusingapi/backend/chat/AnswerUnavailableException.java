package com.llmusingapi.backend.chat;

import java.util.UUID;

/**
 * The user message was saved, but rag-service could not produce an answer.
 * Carries the conversation id so the 503/504 body can include it (Contract A).
 */
public class AnswerUnavailableException extends RuntimeException {

    public enum Reason {
        /** rag-service unreachable or index still loading: 503. */
        UNAVAILABLE,
        /** rag-service did not answer within the read timeout: 504. */
        TIMEOUT
    }

    private final UUID conversationId;
    private final Reason reason;

    public AnswerUnavailableException(UUID conversationId, Reason reason, Throwable cause) {
        super(reason == Reason.TIMEOUT ? "answer timed out" : "answer unavailable", cause);
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

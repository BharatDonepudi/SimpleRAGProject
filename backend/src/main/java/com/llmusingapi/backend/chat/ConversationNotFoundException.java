package com.llmusingapi.backend.chat;

import java.util.UUID;

/** A conversation id was given but no such conversation exists. Maps to 404. */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(UUID conversationId) {
        super("Conversation not found: " + conversationId);
    }
}

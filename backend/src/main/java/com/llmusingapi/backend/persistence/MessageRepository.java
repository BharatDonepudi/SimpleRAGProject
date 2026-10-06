package com.llmusingapi.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Transcript of one conversation, oldest first. The id is the tie-breaker
     * when two messages share the same timestamp.
     */
    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
}

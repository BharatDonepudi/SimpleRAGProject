package com.llmusingapi.backend.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MessageRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-05T22:14:00.000Z");

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void transcriptIsOrderedByCreatedAtOldestFirst() {
        Conversation conversation = conversationRepository.save(new Conversation(T0));
        Conversation other = conversationRepository.save(new Conversation(T0));

        // Inserted out of chronological order on purpose.
        messageRepository.save(new Message(conversation, Role.ASSISTANT, "answer", T0.plusSeconds(22)));
        messageRepository.save(new Message(other, Role.USER, "someone else", T0.plusSeconds(5)));
        messageRepository.save(new Message(conversation, Role.USER, "question", T0.plusSeconds(1)));

        List<Message> transcript = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());

        assertThat(transcript).extracting(Message::getContent).containsExactly("question", "answer");
        assertThat(transcript).extracting(Message::getRole).containsExactly(Role.USER, Role.ASSISTANT);
    }

    @Test
    void messagesWithSameTimestampKeepInsertionOrder() {
        Conversation conversation = conversationRepository.save(new Conversation(T0));

        messageRepository.save(new Message(conversation, Role.USER, "first", T0));
        messageRepository.save(new Message(conversation, Role.ASSISTANT, "second", T0));

        List<Message> transcript = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());

        assertThat(transcript).extracting(Message::getContent).containsExactly("first", "second");
    }

    @Test
    void persistsRoleTimestampAndLargeContent() {
        Conversation conversation = conversationRepository.save(new Conversation(T0));
        String longAnswer = "x".repeat(10_000) + " end";

        Message saved = messageRepository.save(new Message(conversation, Role.ASSISTANT, longAnswer, T0));
        messageRepository.flush();
        entityManager.clear();

        Message reloaded = messageRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ASSISTANT);
        assertThat(reloaded.getContent()).isEqualTo(longAnswer);
        assertThat(reloaded.getCreatedAt()).isEqualTo(T0);
        assertThat(reloaded.getConversation().getId()).isEqualTo(conversation.getId());
    }

    @Test
    void conversationKeepsItsIdAndCreatedAt() {
        Conversation saved = conversationRepository.save(new Conversation(T0));
        conversationRepository.flush();
        entityManager.clear();

        Conversation reloaded = conversationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(T0);
    }
}

package com.llmusingapi.backend.chat;

import com.llmusingapi.backend.persistence.Conversation;
import com.llmusingapi.backend.persistence.ConversationRepository;
import com.llmusingapi.backend.persistence.Message;
import com.llmusingapi.backend.persistence.MessageRepository;
import com.llmusingapi.backend.persistence.Role;
import com.llmusingapi.backend.rag.RagClient;
import com.llmusingapi.backend.rag.RagTimeoutException;
import com.llmusingapi.backend.rag.RagUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RagClient ragClient;

    public ChatService(ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       RagClient ragClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.ragClient = ragClient;
    }

    /**
     * Stores the user message first, then asks rag-service and stores the answer.
     * No transaction spans the rag call, so the database is not held open for up to 120s.
     *
     * <p>Once the conversation exists, any failure is rethrown as {@link AnswerUnavailableException}
     * carrying the conversation id. The user message stays saved in every case.
     * Failures before the conversation exists (unknown id) pass through unchanged.
     */
    public ChatResponse chat(ChatRequest request) {
        Conversation conversation = resolveConversation(request.conversationId());
        UUID conversationId = conversation.getId();
        try {
            return answerAndStore(conversation, request.message());
        } catch (AnswerUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected failure in conversation {}", conversationId, ex);
            throw new AnswerUnavailableException(conversationId, AnswerUnavailableException.Reason.FAILED, ex);
        }
    }

    private ChatResponse answerAndStore(Conversation conversation, String question) {
        UUID conversationId = conversation.getId();
        messageRepository.save(new Message(conversation, Role.USER, question, ApiTimestamps.now()));

        String answer;
        try {
            answer = ragClient.ask(question);
        } catch (RagUnavailableException ex) {
            log.warn("rag-service unavailable for conversation {}: {}", conversationId, ex.getMessage());
            throw new AnswerUnavailableException(conversationId, AnswerUnavailableException.Reason.UNAVAILABLE, ex);
        } catch (RagTimeoutException ex) {
            log.warn("rag-service timed out for conversation {}", conversationId);
            throw new AnswerUnavailableException(conversationId, AnswerUnavailableException.Reason.TIMEOUT, ex);
        }

        Message reply = new Message(conversation, Role.ASSISTANT, answer, ApiTimestamps.now());
        messageRepository.save(reply);
        return new ChatResponse(conversationId, reply.getContent(), reply.getCreatedAt());
    }

    /** Transcript of a conversation, oldest first. */
    @Transactional(readOnly = true)
    public List<MessageResponse> transcript(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
                .map(m -> new MessageResponse(
                        m.getRole().name().toLowerCase(Locale.ROOT),
                        m.getContent(),
                        m.getCreatedAt()))
                .toList();
    }

    private Conversation resolveConversation(UUID conversationId) {
        if (conversationId == null) {
            return conversationRepository.save(new Conversation(ApiTimestamps.now()));
        }
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }
}

package com.llmusingapi.backend.chat;

import com.llmusingapi.backend.persistence.Conversation;
import com.llmusingapi.backend.persistence.ConversationRepository;
import com.llmusingapi.backend.persistence.Message;
import com.llmusingapi.backend.persistence.MessageRepository;
import com.llmusingapi.backend.persistence.Role;
import com.llmusingapi.backend.rag.RagClient;
import com.llmusingapi.backend.rag.RagFailedException;
import com.llmusingapi.backend.rag.RagTimeoutException;
import com.llmusingapi.backend.rag.RagUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RagClient ragClient;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(conversationRepository, messageRepository, ragClient);
    }

    @Test
    void newConversationSavesUserMessageBeforeCallingRag() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ragClient.ask(anyString())).thenReturn("The main points are ...");

        chatService.chat(new ChatRequest(null, "What are the main points"));

        InOrder order = inOrder(messageRepository, ragClient);
        order.verify(messageRepository).save(any(Message.class));
        order.verify(ragClient).ask("What are the main points");
        order.verify(messageRepository).save(any(Message.class));
    }

    @Test
    void userMessageThenAssistantMessageAreSavedInOrder() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ragClient.ask(anyString())).thenReturn("The main points are ...");

        ChatResponse response = chatService.chat(new ChatRequest(null, "What are the main points"));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        List<Message> messages = saved.getAllValues();

        assertThat(messages.get(0).getRole()).isEqualTo(Role.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("What are the main points");
        assertThat(messages.get(1).getRole()).isEqualTo(Role.ASSISTANT);
        assertThat(messages.get(1).getContent()).isEqualTo("The main points are ...");
        assertThat(messages.get(0).getConversation()).isSameAs(messages.get(1).getConversation());

        assertThat(response.answer()).isEqualTo("The main points are ...");
        assertThat(response.conversationId()).isEqualTo(messages.get(0).getConversation().getId());
        assertThat(response.createdAt()).isEqualTo(messages.get(1).getCreatedAt());
    }

    @Test
    void userMessageIsStoredTrimmed() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ragClient.ask(anyString())).thenReturn("answer");

        chatService.chat(new ChatRequest(null, "  hello  "));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getContent()).isEqualTo("hello");
        verify(ragClient).ask("hello");
    }

    @Test
    void existingConversationIsReusedNotRecreated() {
        Conversation existing = new Conversation(Instant.parse("2026-10-05T22:14:00.000Z"));
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(ragClient.ask(anyString())).thenReturn("answer");

        ChatResponse response = chatService.chat(new ChatRequest(existing.getId(), "follow up"));

        verify(conversationRepository, never()).save(any());
        assertThat(response.conversationId()).isEqualTo(existing.getId());
    }

    @Test
    void unknownConversationIsNotFoundAndNothingIsSaved() {
        UUID unknown = UUID.randomUUID();
        when(conversationRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.chat(new ChatRequest(unknown, "hi")))
                .isInstanceOf(ConversationNotFoundException.class);

        verifyNoInteractions(messageRepository, ragClient);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void ragUnavailableKeepsUserMessageAndCarriesConversationId() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ragClient.ask(anyString())).thenThrow(new RagUnavailableException("down", null));

        assertThatThrownBy(() -> chatService.chat(new ChatRequest(null, "hi")))
                .isInstanceOfSatisfying(AnswerUnavailableException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AnswerUnavailableException.Reason.UNAVAILABLE);
                    assertThat(ex.getConversationId()).isNotNull();
                });

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getContent()).isEqualTo("hi");
    }

    @Test
    void ragTimeoutKeepsUserMessageAndCarriesConversationId() {
        Conversation existing = new Conversation(Instant.parse("2026-10-05T22:14:00.000Z"));
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(ragClient.ask(anyString())).thenThrow(new RagTimeoutException("slow", null));

        assertThatThrownBy(() -> chatService.chat(new ChatRequest(existing.getId(), "hi")))
                .isInstanceOfSatisfying(AnswerUnavailableException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AnswerUnavailableException.Reason.TIMEOUT);
                    assertThat(ex.getConversationId()).isEqualTo(existing.getId());
                });

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void ragFailureCarriesConversationIdAndKeepsUserMessage() {
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ragClient.ask(anyString())).thenThrow(new RagFailedException("chain failed", null));

        assertThatThrownBy(() -> chatService.chat(new ChatRequest(null, "hi")))
                .isInstanceOfSatisfying(AnswerUnavailableException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AnswerUnavailableException.Reason.FAILED);
                    assertThat(ex.getConversationId()).isNotNull();
                    assertThat(ex.getCause()).isInstanceOf(RagFailedException.class);
                });

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void unexpectedErrorAfterConversationCreatedCarriesConversationId() {
        Conversation existing = new Conversation(Instant.parse("2026-10-05T22:14:00.000Z"));
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(ragClient.ask(anyString())).thenReturn("answer");
        // The user message saves, but storing the answer fails.
        when(messageRepository.save(any(Message.class)))
                .thenReturn(null)
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> chatService.chat(new ChatRequest(existing.getId(), "hi")))
                .isInstanceOfSatisfying(AnswerUnavailableException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AnswerUnavailableException.Reason.FAILED);
                    assertThat(ex.getConversationId()).isEqualTo(existing.getId());
                });

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getRole()).isEqualTo(Role.USER);
    }

    @Test
    void unexpectedErrorBeforeConversationExistsPassesThroughUnwrapped() {
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> chatService.chat(new ChatRequest(null, "hi")))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(messageRepository, ragClient);
    }

    @Test
    void transcriptIsOldestFirstWithLowercaseRoles() {
        UUID id = UUID.randomUUID();
        Conversation conversation = new Conversation(Instant.parse("2026-10-05T22:14:00.000Z"));
        when(conversationRepository.existsById(id)).thenReturn(true);
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(id)).thenReturn(List.of(
                new Message(conversation, Role.USER, "question", Instant.parse("2026-10-05T22:14:41.002Z")),
                new Message(conversation, Role.ASSISTANT, "answer", Instant.parse("2026-10-05T22:15:03.120Z"))));

        List<MessageResponse> transcript = chatService.transcript(id);

        assertThat(transcript).containsExactly(
                new MessageResponse("user", "question", Instant.parse("2026-10-05T22:14:41.002Z")),
                new MessageResponse("assistant", "answer", Instant.parse("2026-10-05T22:15:03.120Z")));
    }

    @Test
    void transcriptForUnknownConversationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> chatService.transcript(id))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAscIdAsc(any());
    }
}

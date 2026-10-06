package com.llmusingapi.backend.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    private static final UUID CONVERSATION_ID = UUID.fromString("3f6c2a8e-1b1d-4c7e-9a52-0d8b8e6f1a10");
    private static final String CHAT_PATH = "/api/chat";
    private static final String MESSAGES_PATH = "/api/conversations/" + CONVERSATION_ID + "/messages";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void happyPathReturnsConversationAnswerAndIsoTimestamp() throws Exception {
        when(chatService.chat(any(ChatRequest.class))).thenReturn(
                new ChatResponse(CONVERSATION_ID, "The main points are ...", Instant.parse("2026-10-05T22:15:03.120Z")));

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":null,\"message\":\"What are the main points\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()))
                .andExpect(jsonPath("$.answer").value("The main points are ..."))
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T22:15:03.120Z"));
    }

    @Test
    void blankMessageIsRejectedBeforeReachingService() throws Exception {
        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(chatService, never()).chat(any());
    }

    @Test
    void missingMessageIsRejected() throws Exception {
        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void messageOverTwoThousandCharactersIsRejected() throws Exception {
        String tooLong = "a".repeat(2001);

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(chatService, never()).chat(any());
    }

    @Test
    void messageIsTrimmedBeforeTheLengthCheck() throws Exception {
        // 2000 real characters plus padding: only valid once trimmed.
        String padded = "   " + "a".repeat(2000) + "   ";
        when(chatService.chat(any(ChatRequest.class))).thenReturn(
                new ChatResponse(CONVERSATION_ID, "ok", Instant.parse("2026-10-05T22:15:03.120Z")));

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + padded + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedConversationIdReturnsBadRequestWithoutConversationIdField() throws Exception {
        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"not-a-uuid\",\"message\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.conversationId").doesNotExist());

        verify(chatService, never()).chat(any());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void nonJsonContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("What are the main points"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.conversationId").doesNotExist());

        verify(chatService, never()).chat(any());
    }

    @Test
    void wrongMethodOnKnownChatPathReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get(CHAT_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void wrongMethodOnMessagesPathReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete(MESSAGES_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void unknownConversationOnChatReturnsNotFoundWithoutConversationId() throws Exception {
        when(chatService.chat(any(ChatRequest.class))).thenThrow(new ConversationNotFoundException(CONVERSATION_ID));

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\",\"message\":\"hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.conversationId").doesNotExist());
    }

    @Test
    void ragUnavailableReturns503WithConversationId() throws Exception {
        when(chatService.chat(any(ChatRequest.class))).thenThrow(
                new AnswerUnavailableException(CONVERSATION_ID, AnswerUnavailableException.Reason.UNAVAILABLE, null));

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("The document assistant is not ready yet. Try again shortly."))
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()));
    }

    @Test
    void ragTimeoutReturns504WithConversationId() throws Exception {
        when(chatService.chat(any(ChatRequest.class))).thenThrow(
                new AnswerUnavailableException(CONVERSATION_ID, AnswerUnavailableException.Reason.TIMEOUT, null));

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + CONVERSATION_ID + "\",\"message\":\"hi\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID.toString()));
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(chatService.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(post(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error."))
                .andExpect(jsonPath("$.conversationId").doesNotExist());
    }

    @Test
    void unknownPathReturnsNotFoundJson() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void transcriptReturnsMessagesOldestFirstWithLowercaseRoles() throws Exception {
        when(chatService.transcript(CONVERSATION_ID)).thenReturn(List.of(
                new MessageResponse("user", "What are the main points", Instant.parse("2026-10-05T22:14:41.002Z")),
                new MessageResponse("assistant", "The main points are ...", Instant.parse("2026-10-05T22:15:03.120Z"))));

        mockMvc.perform(get(MESSAGES_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-05T22:14:41.002Z"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].content").value("The main points are ..."));
    }

    @Test
    void transcriptForUnknownConversationReturnsNotFound() throws Exception {
        when(chatService.transcript(CONVERSATION_ID)).thenThrow(new ConversationNotFoundException(CONVERSATION_ID));

        mockMvc.perform(get(MESSAGES_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void transcriptWithInvalidUuidReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/conversations/not-a-uuid/messages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(chatService, never()).transcript(any());
    }
}

package com.llmusingapi.backend.chat;

import com.llmusingapi.backend.rag.RagClient;
import com.llmusingapi.backend.rag.RagTimeoutException;
import com.llmusingapi.backend.rag.RagUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack inside the backend: real controller, service, repositories and in-memory H2.
 * rag-service is replaced by a mock, so no network or Ollama is needed.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chatflow;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ChatFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagClient ragClient;

    @Test
    void successfulChatIsStoredAndReadBackInOrder() throws Exception {
        when(ragClient.ask(anyString())).thenReturn("The main points are ...");

        MvcResult chat = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  What are the main points  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The main points are ..."))
                .andReturn();
        String conversationId = com.jayway.jsonpath.JsonPath.read(chat.getResponse().getContentAsString(), "$.conversationId");

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("What are the main points"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].content").value("The main points are ..."));
    }

    @Test
    void ragUnavailableOnNewConversationReturns503WithConversationIdAndKeepsUserMessage() throws Exception {
        when(ragClient.ask(anyString())).thenThrow(new RagUnavailableException("down", null));

        MvcResult failed = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"are you there\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.conversationId").exists())
                .andReturn();
        String conversationId = com.jayway.jsonpath.JsonPath.read(failed.getResponse().getContentAsString(), "$.conversationId");

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("are you there"));
    }

    @Test
    void ragTimeoutOnExistingConversationReturns504WithSameConversationId() throws Exception {
        when(ragClient.ask(anyString())).thenReturn("first answer");
        MvcResult first = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"first\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String conversationId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.conversationId");

        when(ragClient.ask(anyString())).thenThrow(new RagTimeoutException("slow", null));
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"message\":\"second\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.conversationId").value(conversationId));

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].role").value("user"))
                .andExpect(jsonPath("$[2].content").value("second"));
    }

    @Test
    void unknownConversationReturns404WithoutConversationId() throws Exception {
        String unknown = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + unknown + "\",\"message\":\"hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.conversationId").doesNotExist());

        mockMvc.perform(get("/api/conversations/" + unknown + "/messages"))
                .andExpect(status().isNotFound());
    }
}

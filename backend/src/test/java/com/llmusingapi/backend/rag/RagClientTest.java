package com.llmusingapi.backend.rag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class RagClientTest {

    private static final String BASE_URL = "http://rag.test";
    private static final String ASK_URL = BASE_URL + "/ask";

    private MockRestServiceServer server;
    private RagClient ragClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        ragClient = new RagClient(builder.build());
    }

    @AfterEach
    void verifyAllExpectationsMet() {
        server.verify();
    }

    @Test
    void returnsAnswerOnSuccess() {
        server.expect(requestTo(ASK_URL))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value("What are the main points"))
                .andRespond(withSuccess("{\"answer\":\"The main points are ...\",\"extra\":1}", MediaType.APPLICATION_JSON));

        String answer = ragClient.ask("What are the main points");

        assertThat(answer).isEqualTo("The main points are ...");
    }

    @Test
    void indexLoadingFromRagIsUnavailable() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withStatus(SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"index loading\"}"));

        assertThatThrownBy(() -> ragClient.ask("question"))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void readTimeoutIsTimeout() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> ragClient.ask("question"))
                .isInstanceOf(RagTimeoutException.class);
    }

    @Test
    void connectionRefusedIsUnavailable() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> ragClient.ask("question"))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void connectTimeoutIsUnavailableNotTimeout() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withException(new SocketTimeoutException("connect timed out")));

        assertThatThrownBy(() -> ragClient.ask("question"))
                .isInstanceOf(RagUnavailableException.class);
    }

    @Test
    void chainFailureFromRagIsRagFailed() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> ragClient.ask("question"))
                .isInstanceOf(RagFailedException.class);
    }

    @Test
    void responseWithoutAnswerIsRagFailed() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ragClient.ask("question"))
                .isInstanceOf(RagFailedException.class);
    }
}

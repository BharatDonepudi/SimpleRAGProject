package com.llmusingapi.backend.rag;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Calls rag-service {@code POST /ask} (Contract B) and maps its failures to the
 * exceptions the chat layer understands.
 */
@Component
public class RagClient {

    private final RestClient restClient;

    public RagClient(RestClient ragRestClient) {
        this.restClient = ragRestClient;
    }

    /**
     * @throws RagUnavailableException rag-service refused the connection or its index is still loading
     * @throws RagTimeoutException     rag-service did not answer within the read timeout
     * @throws RagFailedException      rag-service failed with any other error
     */
    public String ask(String question) {
        AskResponse body;
        try {
            body = restClient.post()
                    .uri("/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AskRequest(question))
                    .retrieve()
                    .body(AskResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw new RagUnavailableException("rag-service is not ready (index loading)", ex);
            }
            throw new RagFailedException("rag-service returned HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            if (isReadTimeout(ex)) {
                throw new RagTimeoutException("rag-service did not answer in time", ex);
            }
            throw new RagUnavailableException("rag-service is unreachable", ex);
        }

        if (body == null || body.answer() == null) {
            throw new RagFailedException("rag-service returned no answer", null);
        }
        return body.answer();
    }

    /**
     * A read timeout is a timeout; a connect timeout means the service is not reachable.
     */
    private static boolean isReadTimeout(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (!(cause instanceof SocketTimeoutException)) {
            return false;
        }
        String message = cause.getMessage();
        boolean connectPhase = message != null && message.toLowerCase(Locale.ROOT).contains("connect");
        return !connectPhase;
    }
}

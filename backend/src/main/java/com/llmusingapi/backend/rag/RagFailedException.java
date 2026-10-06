package com.llmusingapi.backend.rag;

/**
 * rag-service answered with an error other than 503 (for example 500 when the chain
 * failed, or 422), or with a body that has no answer. Surfaces to the client as a 500.
 */
public class RagFailedException extends RuntimeException {

    public RagFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

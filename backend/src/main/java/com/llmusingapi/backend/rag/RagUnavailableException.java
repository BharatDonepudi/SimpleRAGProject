package com.llmusingapi.backend.rag;

/**
 * rag-service cannot be reached (connection refused, DNS, reset) or reports 503
 * because its index is still loading.
 */
public class RagUnavailableException extends RuntimeException {

    public RagUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

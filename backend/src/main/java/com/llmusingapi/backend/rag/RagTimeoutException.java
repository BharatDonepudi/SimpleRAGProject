package com.llmusingapi.backend.rag;

/**
 * rag-service accepted the connection but did not answer within the read timeout.
 */
public class RagTimeoutException extends RuntimeException {

    public RagTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.llmusingapi.backend.rag;

/** Contract B request body for {@code POST /ask}. */
public record AskRequest(String question) {
}

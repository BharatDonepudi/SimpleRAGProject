package com.llmusingapi.backend.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Contract B response body for {@code POST /ask}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AskResponse(String answer) {
}

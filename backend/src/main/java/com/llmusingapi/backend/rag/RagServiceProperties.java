package com.llmusingapi.backend.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for the rag-service HTTP client (Contract B).
 *
 * @param url         base URL of rag-service, e.g. http://localhost:8000
 * @param readTimeout how long to wait for an answer; rag-service calls take 10-60s on a local model
 */
@ConfigurationProperties(prefix = "rag.service")
public record RagServiceProperties(
        @DefaultValue("http://localhost:8000") String url,
        @DefaultValue("120s") Duration readTimeout) {
}

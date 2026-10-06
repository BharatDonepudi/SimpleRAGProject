package com.llmusingapi.backend.chat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps in Contract A are ISO-8601 UTC with millisecond precision, e.g.
 * {@code 2026-10-05T22:15:03.120Z}.
 */
public final class ApiTimestamps {

    public static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    private ApiTimestamps() {
    }

    /** Current time truncated to milliseconds so the stored value matches the API output. */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}

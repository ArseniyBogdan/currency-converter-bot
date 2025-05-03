package ru.spbstu.hsai.healthcheck.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VaultHealthResponse(
        @JsonProperty("initialized")
        boolean initialized,
        @JsonProperty("sealed")
        boolean sealed,
        @JsonProperty("standby")
        boolean standby,
        @JsonProperty("server_time_utc")
        long serverTimeUtc
) {
}

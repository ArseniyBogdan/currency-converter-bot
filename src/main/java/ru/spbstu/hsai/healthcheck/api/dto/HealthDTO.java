package ru.spbstu.hsai.healthcheck.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record HealthDTO(
        @JsonProperty("status")
        HealthStatus status,
        @JsonProperty("components")
        HealthComponentsDTO components,
        @JsonProperty("version")
        String version,
        @JsonProperty("authors")
        List<String> authors,
        @JsonProperty("timestamp")
        String timestamp
) {
}

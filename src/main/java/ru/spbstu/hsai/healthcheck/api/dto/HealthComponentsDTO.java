package ru.spbstu.hsai.healthcheck.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthComponentsDTO(
        @JsonProperty("mongoDB")
        HealthStatus mongoDB,
        @JsonProperty("rabbitMQ")
        HealthStatus rabbitMQ,
        @JsonProperty("openExchangeRates")
        HealthStatus openExchangeRates,
        @JsonProperty("telegramAPI")
        HealthStatus telegramAPI,
        @JsonProperty("vault")
        HealthStatus vault
) {
}

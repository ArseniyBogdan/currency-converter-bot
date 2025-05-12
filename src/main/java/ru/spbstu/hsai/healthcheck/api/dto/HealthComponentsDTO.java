package ru.spbstu.hsai.healthcheck.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Состояние компонентов системы")
public record HealthComponentsDTO(
        @JsonProperty("mongoDB")
        @Schema(description = "Статус MongoDB")
        HealthStatus mongoDB,

        @JsonProperty("rabbitMQ")
        @Schema(description = "Статус RabbitMQ")
        HealthStatus rabbitMQ,

        @JsonProperty("openExchangeRates")
        @Schema(description = "Статус OpenExchangeRates API")
        HealthStatus openExchangeRates,

        @JsonProperty("telegramAPI")
        @Schema(description = "Статус Telegram API")
        HealthStatus telegramAPI,

        @JsonProperty("vault")
        @Schema(description = "Статус Vault")
        HealthStatus vault
) {
}

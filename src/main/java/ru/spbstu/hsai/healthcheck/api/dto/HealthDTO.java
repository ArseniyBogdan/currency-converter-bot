package ru.spbstu.hsai.healthcheck.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Общее состояние системы")
public record HealthDTO(
        @JsonProperty("status")
        @Schema(description = "Общий статус системы")
        HealthStatus status,

        @JsonProperty("components")
        @Schema(description = "Состояние отдельных компонентов")
        HealthComponentsDTO components,

        @JsonProperty("version")
        @Schema(description = "Версия системы", example = "1.0.0")
        String version,

        @JsonProperty("authors")
        @Schema(description = "Список авторов системы")
        List<String> authors,

        @JsonProperty("timestamp")
        @Schema(description = "Время проверки", format = "date-time")
        String timestamp
) {
}
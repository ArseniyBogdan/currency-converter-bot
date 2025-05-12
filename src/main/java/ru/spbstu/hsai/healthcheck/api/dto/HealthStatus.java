package ru.spbstu.hsai.healthcheck.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Статус компонента")
public enum HealthStatus {
    @Schema(description = "Компонент работает") UP,
    @Schema(description = "Компонент недоступен") DOWN
}

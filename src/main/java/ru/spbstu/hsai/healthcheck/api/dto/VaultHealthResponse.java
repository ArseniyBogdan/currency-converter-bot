package ru.spbstu.hsai.healthcheck.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ответ Vault при проверке здоровья")
public record VaultHealthResponse(
        @JsonProperty("initialized")
        @Schema(description = "Инициализирован ли Vault")
        boolean initialized,

        @JsonProperty("sealed")
        @Schema(description = "Запечатан ли Vault")
        boolean sealed,

        @JsonProperty("standby")
        @Schema(description = "Находится ли в режиме standby")
        boolean standby,

        @JsonProperty("server_time_utc")
        @Schema(description = "Время сервера в UTC (timestamp)")
        long serverTimeUtc
) {
}

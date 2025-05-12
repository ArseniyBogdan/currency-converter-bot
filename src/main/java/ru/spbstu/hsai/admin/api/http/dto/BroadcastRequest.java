package ru.spbstu.hsai.admin.api.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Запрос на рассылку уведомлений")
public class BroadcastRequest {
    @Schema(description = "Текст сообщения", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("message") String message;
}

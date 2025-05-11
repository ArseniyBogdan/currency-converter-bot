package ru.spbstu.hsai.history.api.amqp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoryEvent {
    private Long chatId;
    private String commandType;
    private String currencyCode;
    private Map<String, Object> payload;
    private LocalDateTime created;
}
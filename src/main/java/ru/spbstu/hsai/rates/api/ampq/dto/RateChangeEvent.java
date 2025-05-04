package ru.spbstu.hsai.rates.api.ampq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Событие изменения курса валютной пары,
 * Отправляется в RabbitMQ, когда происходит
 * подгрузка данных с OpenExchangeRates.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateChangeEvent {
    @JsonProperty("baseCurrency")
    @NotNull
    private String baseCurrency;

    @JsonProperty("targetCurrency")
    @NotNull
    private String targetCurrency;

    @JsonProperty("oldRate")
    private BigDecimal oldRate;

    @JsonProperty("newRate")
    private BigDecimal newRate;

    @JsonProperty("changePercent")
    private BigDecimal changePercent;

    @JsonProperty("updated")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime updated;
}

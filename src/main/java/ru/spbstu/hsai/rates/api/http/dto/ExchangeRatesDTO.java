package ru.spbstu.hsai.rates.api.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Ответ API OpenExchangeRates с курсами валют
 */
@Data
public class ExchangeRatesDTO {
    /**
     * Таймстамп последнего обновления
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * Базовая валюта
     */
    @JsonProperty("base")
    private String base;

    /**
     * Map с курсами валют (код валюты -> курс к базовой)
     */
    @JsonProperty("rates")
    private Map<String, BigDecimal> rates;
}

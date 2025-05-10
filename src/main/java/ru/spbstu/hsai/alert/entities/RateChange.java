package ru.spbstu.hsai.alert.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateChange {
    private String baseCurrency;
    private String targetCurrency;
    private BigDecimal oldRate;
    private BigDecimal newRate;
    private BigDecimal changePercent;
}
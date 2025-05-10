package ru.spbstu.hsai.alert.entities;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AlertResult {
    Long chatId;
    String baseCurrency;
    String targetCurrency;
    BigDecimal newRate;
    BigDecimal changePercent;
    String reason;
}

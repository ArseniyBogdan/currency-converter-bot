package ru.spbstu.hsai.rates.entities;

import jakarta.persistence.Id;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Валютная пара
 */

@Data
@RequiredArgsConstructor
@Document(collection = "currency_pairs")
public class CurrencyPairDBO {
    @Id
    private Long currencyPairId;
    private CurrencyDBO baseCurrency;
    private CurrencyDBO targetCurrency;
    private BigDecimal currentRate;
    private Timestamp updated;
}

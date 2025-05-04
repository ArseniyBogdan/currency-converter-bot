package ru.spbstu.hsai.rates.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Валютная пара
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "currency_pairs")
public class CurrencyPairDBO {
    @Id
    @Field("_id")
    private ObjectId currencyPairId;
    private String baseCurrency;
    private String targetCurrency;
    private BigDecimal currentRate;
    private LocalDateTime updated;
}

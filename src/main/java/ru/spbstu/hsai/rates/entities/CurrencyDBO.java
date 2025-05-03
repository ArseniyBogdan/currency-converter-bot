package ru.spbstu.hsai.rates.entities;

import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.sql.Timestamp;
import java.util.Set;

/**
 * Конкретная валюта
 */

@Data
@Document(collection = "currencies")
public class CurrencyDBO {
    @Id
    private String code;
    private String name;
    private Set<CurrencyPairReference> pairsAsBase;
    private Set<CurrencyPairReference> pairsAsTarget;
    private Timestamp updated;
}
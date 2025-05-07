package ru.spbstu.hsai.rates.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Конкретная валюта
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "currencies")
public class CurrencyDBO {
    @Id
    private String code;
    private String name;
    private LocalDateTime updated;
}
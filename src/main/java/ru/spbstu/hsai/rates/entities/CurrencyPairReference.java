package ru.spbstu.hsai.rates.entities;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Ссылка на валютную пару
 */

@Data
@AllArgsConstructor
public class CurrencyPairReference {
    private Long pairId;
    private String displayName;
}

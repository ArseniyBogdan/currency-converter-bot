package ru.spbstu.hsai.mathcurr;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface RatesForMathService {
    Mono<BigDecimal> getBigDecimalExchangeRate(String baseCurrency, String targetCurrency);
}

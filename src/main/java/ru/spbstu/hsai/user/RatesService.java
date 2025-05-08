package ru.spbstu.hsai.user;

import org.bson.types.ObjectId;
import reactor.core.publisher.Mono;

public interface RatesService {
    Mono<ObjectId> getCurrencyPairId(String baseCurrency, String targetCurrency);
    Mono<Boolean> isCurrencyExists(String currencyCode);
    Mono<String> getDefaultPairString(ObjectId pairId);
}

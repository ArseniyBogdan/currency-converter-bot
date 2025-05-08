package ru.spbstu.hsai.rates.dao;

import org.bson.types.ObjectId;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;

@Repository
public interface CurrencyPairDAO extends ReactiveCrudRepository<CurrencyPairDBO, ObjectId> {
    Mono<CurrencyPairDBO> findByBaseCurrencyAndTargetCurrency(String baseCurrency, String targetCurrency);
}
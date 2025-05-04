package ru.spbstu.hsai.rates.dao;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;

@Repository
public interface CurrencyDAO extends ReactiveCrudRepository<CurrencyDBO, String> {
    Mono<CurrencyDBO> findByCode(String code);
}

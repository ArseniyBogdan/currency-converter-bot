package ru.spbstu.hsai.rates.dao;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;

@Repository
public interface CurrencyDAO extends ReactiveCrudRepository<CurrencyDBO, String> {
}

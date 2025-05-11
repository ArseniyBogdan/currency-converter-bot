package ru.spbstu.hsai.alert.dao;

import org.bson.types.ObjectId;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import ru.spbstu.hsai.alert.entities.AlertDBO;

@Repository
public interface AlertDAO extends ReactiveCrudRepository<AlertDBO, ObjectId> {
    Flux<AlertDBO> findAllByChatId(Long chatId);
    Flux<AlertDBO> findByBaseCurrencyAndTargetCurrency(String baseCurrency, String targetCurrency);
}
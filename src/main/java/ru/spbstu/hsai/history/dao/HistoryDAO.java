package ru.spbstu.hsai.history.dao;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.entities.HistoryDBO;

import java.time.LocalDateTime;

@Repository
public interface HistoryDAO extends ReactiveMongoRepository<HistoryDBO, ObjectId> {
    @Query("{'chatId': ?0, 'created': {$gte: ?1, $lte: ?2}, 'currencyCode': ?3}")
    Flux<HistoryDBO> findByDateRangeAndCurrency(
            Long chatId,
            LocalDateTime start,
            LocalDateTime end,
            String currencyCode
    );

    @Query("{'chatId': ?0, 'created': {$gte: ?0, $lte: ?1}}")
    Flux<HistoryDBO> findByDateRange(
            Long chatId,
            LocalDateTime start,
            LocalDateTime end
    );

    Flux<HistoryDBO> findByChatId(Long chatId);

    @Query("{ 'chatId': ?0 }")
    Mono<Void> deleteByChatId(Long chatId);
}

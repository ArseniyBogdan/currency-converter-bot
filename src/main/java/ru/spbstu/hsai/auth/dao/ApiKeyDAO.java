package ru.spbstu.hsai.auth.dao;

import org.bson.types.ObjectId;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.auth.entities.ApiKeyDBO;

@Repository
public interface ApiKeyDAO extends ReactiveCrudRepository<ApiKeyDBO, ObjectId> {
    Mono<ApiKeyDBO> findByKey(String key);
}

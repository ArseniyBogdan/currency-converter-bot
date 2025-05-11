package ru.spbstu.hsai.history.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import ru.spbstu.hsai.history.entities.HistoryDBO;

import java.util.concurrent.TimeUnit;

@Configuration
public class HistoryTTLConfig {

    @Bean
    public ReactiveIndexOperations historyTTLIndex(ReactiveMongoTemplate mongoTemplate) {
        ReactiveIndexOperations indexOps = mongoTemplate
                .indexOps(HistoryDBO.class);

        indexOps.ensureIndex(new Index()
                .on("created", Sort.Direction.ASC)
                .expire(30, TimeUnit.DAYS));

        return indexOps;
    }

}

package ru.spbstu.hsai.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

@Configuration
@EnableReactiveMongoRepositories(basePackages = {
        "ru.spbstu.hsai.*",
})
public class MongoConfig {

    @Bean
    public MongoClient mongoClient(
            @Value("${mongo_uri}") String uri,
            @Value("${mongo_username}") String username,
            @Value("${mongo_password}") String password
    ) {
        return MongoClients.create(
                MongoClientSettings.builder()
                        .applyConnectionString(new ConnectionString(uri))
                        .credential(MongoCredential.createCredential(
                                username,
                                "admin",
                                password.toCharArray()))
                        .build());
    }


    // Создание ReactiveMongoTemplate
    @Bean
    public ReactiveMongoTemplate reactiveMongoTemplate(MongoClient mongoClient) {
        return new ReactiveMongoTemplate(mongoClient, "currency_bot");
    }

}

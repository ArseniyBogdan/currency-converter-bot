package ru.spbstu.hsai.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
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

    @Bean
    public MongoTemplate mongoTemplate(
            MongoClient mongoClient
    ) {
        return new MongoTemplate(mongoClient, "currency_bot");
    }

}

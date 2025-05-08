package ru.spbstu.hsai.admin;

import reactor.core.publisher.Mono;

public interface ApiKeyService {
    Mono<Boolean> checkKeyRevoked(String apiKey);
}

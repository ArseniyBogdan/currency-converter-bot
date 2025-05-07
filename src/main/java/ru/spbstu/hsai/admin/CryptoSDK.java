package ru.spbstu.hsai.admin;

import reactor.core.publisher.Mono;

public interface CryptoSDK {
    Mono<String> encrypt(String plaintext);
    Mono<String> decrypt(String ciphertext);
}

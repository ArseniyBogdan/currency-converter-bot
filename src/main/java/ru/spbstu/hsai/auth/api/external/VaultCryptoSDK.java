package ru.spbstu.hsai.auth.api.external;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.Plaintext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Сервис для шифрования,
 * используется для шифрования ключей в mongoDB
 */

@Service
@RequiredArgsConstructor
public class VaultCryptoSDK {
    private final VaultTemplate vaultTemplate;

    public Mono<String> encrypt(String plaintext) {
        return Mono.fromCallable(() ->
                vaultTemplate.opsForTransit()
                        .getHmac("api-keys", Plaintext.of(plaintext)).getHmac()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> decrypt(String ciphertext) {
        return Mono.fromCallable(() ->
                vaultTemplate.opsForTransit()
                        .decrypt("api-keys", ciphertext)
        ).subscribeOn(Schedulers.boundedElastic());
    }
}

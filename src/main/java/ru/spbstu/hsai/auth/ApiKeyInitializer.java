package ru.spbstu.hsai.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.auth.api.external.VaultCryptoSDK;
import ru.spbstu.hsai.auth.dao.ApiKeyDAO;
import ru.spbstu.hsai.auth.entities.ApiKeyDBO;

import java.util.UUID;

/**
 * Инициализация API-ключей при первом запуске,
 * генерирует 3 штуки
 */

@Component
@RequiredArgsConstructor
public class ApiKeyInitializer {

    private final ApiKeyDAO apiKeyDAO;
    private final VaultCryptoSDK cryptoService;

    public void run() {
        System.out.println("Starting initialization of API keys...");
        apiKeyDAO.count()
                .doOnNext(count -> System.out.println("Number of Api keys: " + count))
                .filter(count -> count == 0)
                .flatMapMany(exists -> {
                    System.out.println("Key generation...");
                    return generateInitialKeys();
                })
                .flatMap(this::saveEncryptedKey)
                .subscribe(
                        key -> System.out.println("Key created:" + key.getId()),
                        error -> System.out.println("Error:" + error.getMessage()),
                        () -> System.out.println("Initialization is completed")
                );
    }

    private Flux<ApiKeyDBO> generateInitialKeys() {
        return Flux.range(1, 3)
                .map(i -> {
                            ApiKeyDBO apiKey = new ApiKeyDBO(
                                    null,
                                    (long) i,
                                    "key-" + UUID.randomUUID(),
                                    false
                            );
                            return apiKey;
                        }
                );
    }

    private Mono<ApiKeyDBO> saveEncryptedKey(ApiKeyDBO key) {
        return cryptoService.encrypt(key.getKey())
                .doOnNext(encrypted -> System.out.println("Generated " + key.getAdminId() + " base ApiKey: " + key.getKey()))
                .flatMap(encryptedKey -> {
                    key.setKey(encryptedKey);
                    return apiKeyDAO.save(key);
                });
    }
}
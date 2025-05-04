package ru.spbstu.hsai.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.auth.api.external.VaultCryptoSDK;
import ru.spbstu.hsai.auth.dao.ApiKeyDAO;
import ru.spbstu.hsai.auth.entities.ApiKeyDBO;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final VaultCryptoSDK vaultCryptoSDK;
    private final ApiKeyDAO apiKeyRepository;

    public Mono<Boolean> checkKeyRevoked(
            String apiKey
    ){
        return vaultCryptoSDK.encrypt(apiKey)
                .flatMap(apiKeyRepository::findByKey)
                .map(ApiKeyDBO::getRevoked);
    }
}

package ru.spbstu.hsai.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.admin.CryptoSDK;
import ru.spbstu.hsai.admin.dao.ApiKeyDAO;
import ru.spbstu.hsai.admin.entities.ApiKey;
import ru.spbstu.hsai.admin.entities.ApiKeyDBO;

import javax.ws.rs.NotFoundException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ru.spbstu.hsai.admin.ApiKeyService {
    private final AdminService adminService;
    private final CryptoSDK cryptoSDK;
    private final ApiKeyDAO apiKeyDAO;


    public Mono<Boolean> checkKeyRevoked(
            String apiKey
    ){
        return cryptoSDK.encrypt(apiKey)
                .flatMap(keyEnc -> {
                    log.info("Enc " + keyEnc);
                    return apiKeyDAO.findByKey(keyEnc);
                })
                .map(ApiKeyDBO::getRevoked);
    }

    public Mono<ApiKey> generateKey(ObjectId adminId) {
        String rawKey = "key-" + UUID.randomUUID();
        return adminService.getAdminById(adminId)
                .switchIfEmpty(Mono.error(new NotFoundException("Admin not found by id: " + adminId)))
                .then(cryptoSDK.encrypt(rawKey))
                .flatMap(encryptedKey -> {
                    log.info("Generate key for adminId: {} key: {}, encryptedKey", adminId, rawKey);
                    apiKeyDAO.save(new ApiKeyDBO(null, adminId, encryptedKey, false));
                    return apiKeyDAO.save(new ApiKeyDBO(null, adminId, encryptedKey, false)).then(Mono.just(new ApiKey(adminId, rawKey)));
                });
    }

    public Mono<Void> revokeKey(ObjectId adminId) {
        return apiKeyDAO.findByAdminId(adminId)
                .flatMap(key -> {
                    key.setRevoked(true);
                    return apiKeyDAO.save(key);
                })
                .then();
    }

}

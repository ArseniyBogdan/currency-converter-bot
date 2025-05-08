package ru.spbstu.hsai.admin;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import ru.spbstu.hsai.admin.dao.ApiKeyDAO;
import ru.spbstu.hsai.admin.entities.ApiKey;
import ru.spbstu.hsai.admin.service.AdminService;
import ru.spbstu.hsai.admin.service.ApiKeyServiceImpl;

/**
 * Инициализация API-ключей при первом запуске,
 * генерирует 3 штуки
 */

@Component
@RequiredArgsConstructor
public class ApiKeyInitializer {

    private final ApiKeyDAO apiKeyDAO;
    private final ApiKeyServiceImpl apiKeyService;
    private final AdminService adminService;

    public void run() {
        System.out.println("Starting initialization of API keys...");
        apiKeyDAO.count()
                .doOnNext(count -> System.out.println("Number of Api keys: " + count))
                .filter(count -> count == 0)
                .flatMapMany(exists -> {
                    System.out.println("Key generation...");
                    return generateInitialKeys();
                })
                .subscribe(
                        key -> System.out.println("Key created:" + key.getKey()),
                        error -> System.out.println("Error:" + error.getMessage()),
                        () -> System.out.println("Initialization is completed")
                );
    }

    private Flux<ApiKey> generateInitialKeys() {
        return Flux.just(
                Pair.of("Арсений", "Богдан"),
                Pair.of("Дарья", "Яшнова"),
                Pair.of("Ксения", "Шклярова")
        ).flatMap(pair ->
            adminService.createAdmin(pair.getLeft(), pair.getRight())
        ).flatMap(adminDBO ->
            apiKeyService.generateKey(adminDBO.getId())
        );
    }
}
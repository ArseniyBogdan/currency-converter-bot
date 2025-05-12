package ru.spbstu.hsai.healthcheck.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.spbstu.hsai.healthcheck.api.dto.HealthComponentsDTO;
import ru.spbstu.hsai.healthcheck.api.dto.HealthDTO;
import ru.spbstu.hsai.healthcheck.api.dto.HealthStatus;
import ru.spbstu.hsai.healthcheck.api.dto.VaultHealthResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping
@Slf4j
@Tag(name = "Health Check", description = "Мониторинг состояния системы")
public class HealthCheckController {
    private final ReactiveMongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final WebClient webClientExchangeRates;
    private final WebClient webClientTelegram;
    private final VaultTemplate vaultTemplate;

    @Value("${api_key}") String openExchangeRatesToken;

    @Autowired
    public HealthCheckController(
            ReactiveMongoTemplate mongoTemplate,
            RabbitTemplate rabbitTemplate,
            @Qualifier("exchangeRatesWebClient") WebClient webClientExchangeRates,
            @Qualifier("telegramWebClient") WebClient webClientTelegram,
            VaultTemplate vaultTemplate)
    {
        this.mongoTemplate = mongoTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.webClientExchangeRates = webClientExchangeRates;
        this.webClientTelegram = webClientTelegram;
        this.vaultTemplate = vaultTemplate;
    }

    @Operation(
            summary = "Проверить состояние кластера",
            description = "Возвращает состояние всех сервисов",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Все компоненты работают",
                            content = @Content(schema = @Schema(implementation = HealthDTO.class))),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Один или несколько компонентов недоступны",
                            content = @Content(schema = @Schema(implementation = HealthDTO.class))
                    )
            }
    )
    @GetMapping("/healthcheck")
    public Mono<ResponseEntity<HealthDTO>> healthCheck() {
        return Mono.zip(
                        checkMongoDB(),
                        checkRabbitMQ(),
                        checkOpenExchangeRates(),
                        checkVault(),
                        checkTelegramAPI()
                )
                .map(tuple -> {
                    boolean mongoStatus = tuple.getT1();
                    boolean rabbitStatus = tuple.getT2();
                    boolean exchangeStatus = tuple.getT3();
                    boolean vaultStatus = tuple.getT4();
                    boolean telegramStatus = tuple.getT5();

                    HealthStatus overallStatus = (mongoStatus && rabbitStatus && exchangeStatus && vaultStatus && telegramStatus)
                            ? HealthStatus.UP : HealthStatus.DOWN;

                    return ResponseEntity.status(overallStatus == HealthStatus.UP ? 200 : 503)
                            .body(new HealthDTO(
                                    overallStatus,
                                    new HealthComponentsDTO(
                                            status(mongoStatus),
                                            status(rabbitStatus),
                                            status(exchangeStatus),
                                            status(vaultStatus),
                                            status(telegramStatus)
                                    ),
                                    "1.0.0",
                                    List.of("Богдан Арсений", "Яшнова Дарья", "Шклярова Ксения"),
                                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            ));
                });
    }

    private HealthStatus status(boolean condition) {
        return condition ? HealthStatus.UP : HealthStatus.DOWN;
    }

    private Mono<Boolean> checkVault() {
        return Mono.fromCallable(() -> {
                    try {
                        VaultHealthResponse response = vaultTemplate.doWithSession(restOperations ->
                                restOperations.getForEntity(
                                        "/sys/health",
                                        VaultHealthResponse.class
                                ).getBody()
                        );
                        return response != null
                                && !response.sealed()
                                && !response.standby();
                    } catch (Exception e) {
                        log.error("Vault check failed", e);
                        return false;
                    }
                }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> checkMongoDB() {
        return Mono.fromCallable(() -> {
                    try {
                        mongoTemplate.executeCommand("{ dbStats: 1 }");
                        return true;
                    } catch (Exception e) {
                        log.error("MongoDB check failed", e);
                        return false;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> checkRabbitMQ() {
        return Mono.fromCallable(() -> {
                    try (Connection connection = rabbitTemplate.getConnectionFactory().createConnection()) {
                        return connection.isOpen();
                    } catch (Exception e) {
                        log.error("RabbitMQ check failed", e);
                        return false;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> checkOpenExchangeRates() {
        return webClientExchangeRates.get()
                .uri("/latest.json?app_id={apiKey}", openExchangeRatesToken)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.just(true);
                    } else {
                        log.error("OpenExchangeRates API error. Status: {}", response.statusCode());
                        return Mono.just(false);
                    }
                })
                .onErrorResume(e -> {
                    log.error("OpenExchangeRates check failed", e);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> checkTelegramAPI() {
        return webClientTelegram.get()
                .uri("/getMe")
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return Mono.just(true);
                    } else {
                        log.error("Telegram API error. Status: {}", response.statusCode());
                        return Mono.just(false);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Telegram check failed", e);
                    return Mono.just(false);
                });
    }
}

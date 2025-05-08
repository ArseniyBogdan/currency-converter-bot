package ru.spbstu.hsai.rates.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.spbstu.hsai.rates.api.ampq.UpdateCurrenciesSDK;
import ru.spbstu.hsai.rates.api.http.dto.ExchangeRatesDTO;
import ru.spbstu.hsai.rates.dao.CurrencyDAO;
import ru.spbstu.hsai.rates.dao.CurrencyPairDAO;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Сервис для обновления валютных курсов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatesService {
    private final UpdateCurrenciesSDK updateCurrenciesSDK;
    private final CurrencyDAO currencyDAO;
    private final CurrencyPairDAO currencyPairDAO;
    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Обновляет данные о валютах в MongoDB
     *
     * @param currencies Map с данными валют (код -> название)
     * @return Mono сигнализирующий о завершении операции
     */
    public Mono<Void> updateCurrencyData(Map<String, String> currencies) {
        return Flux.fromIterable(currencies.entrySet())
                .flatMap(entry -> {
                    CurrencyDBO newCurrency = new CurrencyDBO(
                            entry.getKey(),
                            entry.getValue(),
                            LocalDateTime.now()
                    );

                    return currencyDAO.findByCode(entry.getKey())
                            .switchIfEmpty(currencyDAO.save(newCurrency));
                })
                .then(createAllPossiblePairs());
    }

    private Mono<Void> createAllPossiblePairs() {
        return currencyDAO.findAll()
                .collectList()
                .zipWith(currencyPairDAO.findAll().collectList())
                .flatMap(tuple -> {
                    List<CurrencyDBO> currencies = tuple.getT1();
                    List<CurrencyPairDBO> existingPairs = tuple.getT2();

                    // Создаем Set для быстрого поиска существующих пар
                    Set<Pair<String, String>> existingPairsSet = existingPairs.stream()
                            .map(p -> Pair.of(p.getBaseCurrency(), p.getTargetCurrency()))
                            .collect(Collectors.toSet());

                    // Генерируем все возможные комбинации
                    List<CurrencyPairDBO> newPairs = currencies.stream()
                            .flatMap(base -> currencies.stream()
                                    .filter(target -> !base.equals(target))
                                    .map(target -> Pair.of(base.getCode(), target.getCode()))
                            )
                            .filter(pair -> !existingPairsSet.contains(pair))
                            .map(pair -> new CurrencyPairDBO(
                                    null,
                                    pair.getLeft(),
                                    pair.getRight(),
                                    BigDecimal.ZERO,
                                    LocalDateTime.now()
                            ))
                            .toList();

                    return Flux.fromIterable(newPairs)
                            .buffer(1000)
                            .flatMap(batch -> currencyPairDAO.saveAll(batch).then())
                            .then();
                });
    }

    public Mono<Void> updateCurrencyPairs(ExchangeRatesDTO response) {
        Map<String, BigDecimal> rates = response.getRates();
        log.info("Executing updateCurrencyPairs");

        return currencyPairDAO.findAll()
                .flatMap(pair -> calculateCrossRate(pair, rates))
                .buffer(500) // Пакетная обработка по 500 пар
                .flatMap(batch -> {
                    log.info("Another batch: {}", batch);
                    return Flux.fromIterable(batch)
                            .parallel()
                            .runOn(Schedulers.parallel())
                            .flatMap(this::processPairUpdate)
                            .sequential();
                })
                .then();
    }

    private Mono<Pair<CurrencyPairDBO, BigDecimal>> calculateCrossRate(CurrencyPairDBO pair, Map<String, BigDecimal> rates) {
        String base = pair.getBaseCurrency();
        String target = pair.getTargetCurrency();

        return Mono.fromSupplier(() -> {
            try {
                BigDecimal baseRate = rates.getOrDefault(base, BigDecimal.ONE);
                BigDecimal targetRate = rates.getOrDefault(target, BigDecimal.ONE);

                return targetRate.divide(baseRate, 6, RoundingMode.HALF_UP);
            } catch (ArithmeticException e) {
                log.error("Error calculating rate for {}/{}: {}", base, target, e.getMessage());
                return pair.getCurrentRate();
            }
        }).map(newRate -> {
            BigDecimal oldRate = pair.getCurrentRate();
            pair.setCurrentRate(newRate);
            pair.setUpdated(LocalDateTime.now());
            return Pair.of(pair, oldRate);
        });
    }

    private Mono<Pair<CurrencyPairDBO, BigDecimal>> processPairUpdate(Pair<CurrencyPairDBO, BigDecimal> pair) {
        BigDecimal oldRate = pair.getRight();
        BigDecimal newRate = pair.getLeft().getCurrentRate();
        String baseCurrency = pair.getLeft().getBaseCurrency();
        String targetCurrency = pair.getLeft().getTargetCurrency();

        Query query = new Query(Criteria
                .where("baseCurrency").is(baseCurrency)
                .and("targetCurrency").is(targetCurrency)
        );

        Update update = new Update()
                .set("currentRate", newRate)
                .set("updated", LocalDateTime.now());

        return mongoTemplate.updateFirst(query, update, CurrencyPairDBO.class)
                .flatMap(updateResult -> {
                    if (updateResult.getMatchedCount() == 0){
                        return Mono.error(new RuntimeException("Document not found"));
                    }
                    return mongoTemplate.findOne(query, CurrencyPairDBO.class);
                })
                .map(updated -> Pair.of(updated, oldRate))
                .doOnNext(updated -> {
                    log.debug("Updated {}/{}: {} -> {}",
                            updated.getLeft().getBaseCurrency(),
                            updated.getLeft().getTargetCurrency(),
                            oldRate,
                            updated.getLeft().getCurrentRate()
                    );
                });
    }

    /**
     * Вычисляет процент изменения курса
     *
     * @param oldRate - старый курс
     * @param newRate - новый курс (после изменения)
     */
    private BigDecimal calculateChangePercent(BigDecimal oldRate, BigDecimal newRate) {
        if (oldRate == null || oldRate.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return newRate.subtract(oldRate)
                .divide(oldRate, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Отправляет уведомление об обновлении курса через RabbitMQ
     *
     * @param pair Обновленная валютная пара
     * @return Mono сигнализирующий о завершении операции
     */
    private Mono<Void> sendUpdateNotification(Pair<CurrencyPairDBO, BigDecimal> pair) {
        return Mono.fromCallable(() -> calculateChangePercent(pair.getLeft().getCurrentRate(), pair.getRight()))
                .flatMap(percent -> updateCurrenciesSDK.sendUpdateNotification(pair.getLeft(), pair.getRight(), percent));
    }


    public Mono<ObjectId> getCurrencyPairId(String baseCurrency, String targetCurrency){
        return currencyPairDAO.findByBaseCurrencyAndTargetCurrency(baseCurrency, targetCurrency).map(CurrencyPairDBO::getCurrencyPairId);
    }

    public Mono<Boolean> isCurrencyExists(String currencyCode){
        return currencyDAO.findByCode(currencyCode)
                .map(_ -> true)
                .switchIfEmpty(Mono.just(false));
    }

    public Mono<String> getDefaultPairString(ObjectId pairId){
        return currencyPairDAO.findById(pairId).map( pair ->
                pair.getBaseCurrency() + "/" + pair.getTargetCurrency()
        );
    }
}

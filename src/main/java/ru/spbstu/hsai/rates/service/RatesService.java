package ru.spbstu.hsai.rates.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.rates.api.ampq.UpdateCurrenciesSDK;
import ru.spbstu.hsai.rates.api.http.dto.ExchangeRatesDTO;
import ru.spbstu.hsai.rates.dao.CurrencyDAO;
import ru.spbstu.hsai.rates.dao.CurrencyPairDAO;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.Map;

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

    /**
     * Обновляет данные о валютах в MongoDB
     *
     * @param currencies Map с данными валют (код -> название)
     * @return Mono сигнализирующий о завершении операции
     */
    public Mono<Void> updateCurrencyData(Map<String, String> currencies) {
        return Flux.fromIterable(currencies.entrySet())
                .flatMap(entry -> currencyDAO.existsById(entry.getKey()).flatMap(exists -> {
                            if (!exists) {
                                CurrencyDBO currency = new CurrencyDBO();
                                currency.setCode(entry.getKey());
                                currency.setName(entry.getValue());
                                currency.setUpdated(new Timestamp(System.currentTimeMillis()));
                                // TODO тут надо настроить ссылки на друг друга
                                return currencyDAO.save(currency).then();
                            }
                            return Mono.empty();
                        })
                ).then();
    }

    /**
     * Обновляет курсы валютных пар в MongoDB на основе полученных данных
     *
     * @param response Ответ API с курсами валют
     * @return Mono сигнализирующий о завершении операции
     * @throws ArithmeticException при ошибках вычисления курсов
     */
    public Mono<Void> updateCurrencyPairs(ExchangeRatesDTO response) {
        return currencyPairDAO.findAll()
                .flatMap(pair -> calculateNewRate(pair, response.getRates())
                        .flatMap(newRate -> updatePairInDatabase(pair, newRate.getCurrentRate()))
                        .flatMap(pairWithHistory ->
                                sendUpdateNotification(
                                        pairWithHistory.getLeft(),
                                        pairWithHistory.getRight()
                                )
                        )
                )
                .then();
    }

    /**
     * Вычисляет новый курс для валютной пары
     *
     * @param pair Обновляемая валютная пара
     * @param rates Актуальные курсы валют к USD
     * @return Mono с обновленной валютной парой
     * @throws IllegalArgumentException при отсутствии необходимых курсов
     */
    private Mono<CurrencyPairDBO> calculateNewRate(CurrencyPairDBO pair, Map<String, BigDecimal> rates) {
        String base = pair.getBaseCurrency().getCode();
        String target = pair.getTargetCurrency().getCode();

        return Mono.fromCallable(() -> {
            if ("USD".equals(base)) {
                return rates.getOrDefault(target, pair.getCurrentRate());
            }

            BigDecimal baseRate = rates.get(base);
            BigDecimal targetRate = rates.get(target);
            if (baseRate == null || targetRate == null) {
                return pair.getCurrentRate();
            }

            return targetRate.divide(baseRate, 6, RoundingMode.HALF_UP);
        }).map(newRate -> {
            pair.setCurrentRate(newRate);
            pair.setUpdated(new Timestamp(System.currentTimeMillis()));
            return pair;
        });
    }

    /**
     * Сохраняет обновленную валютную пару в MongoDB
     *
     * @param pair Старая валютная пара, новый курс
     * @return Mono с сохраненной сущностью
     */
    private Mono<Pair<CurrencyPairDBO, BigDecimal>> updatePairInDatabase(CurrencyPairDBO pair, BigDecimal newRate) {
        BigDecimal oldRate = pair.getCurrentRate();
        pair.setCurrentRate(newRate);
        pair.setUpdated(new Timestamp(System.currentTimeMillis()));

        return currencyPairDAO.save(pair)
                .map(updated -> Pair.of(updated, oldRate))
                .doOnSuccess(updated -> log.info("Updated pair: {}", updated.getLeft().getCurrencyPairId()));
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
    private Mono<Void> sendUpdateNotification(CurrencyPairDBO pair, BigDecimal oldRate) {
        return Mono.fromCallable(() -> calculateChangePercent(pair.getCurrentRate(), oldRate))
                .flatMap(percent -> updateCurrenciesSDK.sendUpdateNotification(pair, oldRate, percent));
    }
}

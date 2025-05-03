package ru.spbstu.hsai.rates.api.ampq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.rates.api.ampq.dto.RateChangeEvent;
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateCurrenciesSDK {
    private final RabbitTemplate rabbitTemplate;
    /**
     * Отправляет уведомление об обновлении курса через RabbitMQ
     *
     * @param pair Обновленная валютная пара
     * @param oldRate Старый курс валютной пары
     * @param changePercent Изменения в процентах
     * @return Mono сигнализирующий о завершении операции
     */
    public Mono<Void> sendUpdateNotification(CurrencyPairDBO pair, BigDecimal oldRate, BigDecimal changePercent) {
        return Mono.fromRunnable(() ->
                rabbitTemplate.convertAndSend(
                        "currency-updates",
                        new RateChangeEvent(
                                pair.getBaseCurrency().getCode(),
                                pair.getTargetCurrency().getCode(),
                                oldRate,
                                pair.getCurrentRate(),
                                changePercent,
                                pair.getUpdated()
                        )
                )
        );
    }
}

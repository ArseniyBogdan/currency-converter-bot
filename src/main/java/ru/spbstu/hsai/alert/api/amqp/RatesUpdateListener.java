package ru.spbstu.hsai.alert.api.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.api.amqp.dto.RateChangeEvent;
import ru.spbstu.hsai.alert.entities.RateChange;
import ru.spbstu.hsai.alert.service.RatesUpdateService;
import ru.spbstu.hsai.telegram.CurrencyConverterBot;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatesUpdateListener {
    private final ObjectMapper objectMapper;
    private final CurrencyConverterBot bot;
    private final RatesUpdateService ratesUpdateService;

    @RabbitListener(queues = "currency-converter-bot.rates-updates")
    public void handleRatesUpdate(Message message) {
        try {
            log.info("Received message: {}", new String(message.getBody()));
            RateChangeEvent event = objectMapper.readValue(message.getBody(), RateChangeEvent.class);
            ratesUpdateService.processRateChange(mapToRateChange(event)).flatMap(result ->
                Mono.fromRunnable(() -> sendNotification(result.getChatId(), result.getBaseCurrency(), result.getTargetCurrency(),
                        result.getNewRate(), result.getChangePercent(), result.getReason()))
            ).subscribe(
                    null,
                    error -> log.error("Error processing event", error),
                    () -> log.debug("Finished processing event")
            );
        } catch (Exception e) {
            log.error("Error processing rates update", e);
        }
    }

    private void sendNotification(
            Long chatId,
            String baseCurrency,
            String targetCurrency,
            BigDecimal newRate,
            BigDecimal changePercent,
            String reason
    ) {
        String message = String.format(
                "🚨 Сработало уведомление для %s/%s\n\n" +
                        "Текущий курс: %.4f\n" +
                        "Изменение: %.2f%%\n" +
                        "Причина: %s\n\n" +
                        "Чтобы управлять уведомлениями: /alert_list",
                baseCurrency,
                targetCurrency,
                newRate,
                changePercent,
                reason
        );

        bot.sendMessage(chatId, message);
    }

    private RateChange mapToRateChange(RateChangeEvent event){
        return new RateChange(
                event.getBaseCurrency(),
                event.getTargetCurrency(),
                event.getOldRate(),
                event.getNewRate(),
                event.getChangePercent()
        );
    }

}

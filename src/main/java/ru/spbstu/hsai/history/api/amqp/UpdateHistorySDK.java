package ru.spbstu.hsai.history.api.amqp;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.history.api.amqp.dto.HistoryEvent;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UpdateHistorySDK implements HistorySDK {
    private final RabbitTemplate rabbitTemplate;

    public Mono<Void> saveHistory(Long chatId, String commandType,
                                  String currencyCode, Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
            HistoryEvent message = new HistoryEvent(
                    chatId, commandType, currencyCode, payload, LocalDateTime.now()
            );

            rabbitTemplate.convertAndSend(
                    "currency-converter-bot",
                    "history.save",
                    message
            );
            return null;
        });
    }
}

package ru.spbstu.hsai.history.api.amqp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.spbstu.hsai.history.api.amqp.dto.HistoryEvent;
import ru.spbstu.hsai.history.service.HistoryService;

@Component
@RequiredArgsConstructor
@Slf4j
public class HistoryQueueListener {
    private final HistoryService historyService;

    @RabbitListener(queues = "currency-converter-bot.history-save")
    public void handleHistoryMessage(HistoryEvent message) {
        historyService.saveHistory(
                message.getChatId(),
                message.getCommandType(),
                message.getCurrencyCode(),
                message.getPayload(),
                message.getCreated()
        ).doOnSuccess(__ -> log.info("History saved from message: {}", message))
                .doOnError(e -> log.error("Error saving history from message", e))
                .subscribe();
    }
}

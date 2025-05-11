package ru.spbstu.hsai.alert.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.Map;

/**
 * Обработчик команды /alert_remove для удаления уведомления
 */
@Component
@RequiredArgsConstructor
public class AlertRemoveHandler implements CommandHandler {

    private final AlertService alertService;
    private final HistorySDK historyService;

    @Value("${command.alert.remove.error.id}")
    private String errorAlertNotFound;

    /**
     * Обрабатывает команду /alert_remove, производя удаление уведомления
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом удаления уведомления или сообщением об ошибке
     */
    @Override
    @BotCommand("/alert_remove")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    String[] parts = commandText.split("\\s+");
                    if (parts.length < 2) {
                        return Mono.error(new CCBException(errorAlertNotFound));
                    }

                    return alertService.deleteAlert(parts[1], message.getChatId()).map(result -> {
                        saveHistory(message.getChatId(), commandText, result);
                        return result;
                    });
                })
                .onErrorResume(e -> Mono.just(e.getMessage()));
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String request, String result) {
        historyService.saveHistory(
                chatId,
                "ALERT_REMOVE",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}
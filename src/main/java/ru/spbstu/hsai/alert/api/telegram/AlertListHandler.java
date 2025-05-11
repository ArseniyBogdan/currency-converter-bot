package ru.spbstu.hsai.alert.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.Map;

/**
 * Обработчик команды /alert_list для получения списка уведомлений
 */
@Component
@RequiredArgsConstructor
public class AlertListHandler implements CommandHandler {

    private final AlertService alertService;
    private final HistorySDK historyService;

    @Value("${command.alert.list.success.empty}")
    private String commandAlertListEmpty;

    @Value("${command.alert.list.success.filled}")
    private String commandAlertSuccess;

    @Value("${command.alert.list.error.chat}")
    private String commandChatNotFound;

    /**
     * Обрабатывает команду /alert_list, производя получение списка уведомлений
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом получения списка уведомлений или сообщением об ошибке
     */
    @Override
    @BotCommand("/alert_list")
    public Mono<String> handle(Message message) {
        return alertService.getAllAlertsByChatId(message.getChatId())
                .flatMap(alerts -> {
                    if (alerts.isEmpty()) {
                        return Mono.just(commandAlertListEmpty);
                    }

                    StringBuilder sb = new StringBuilder(commandAlertSuccess);
                    for (int i = 0; i < alerts.size(); i++) {
                        AlertDBO alert = alerts.get(i);
                        sb.append(i + 1).append(". ")
                                .append(formatAlert(alert))
                                .append("\n");
                    }
                    return Mono.just(sb.toString()).map(result -> {
                        saveHistory(message.getChatId(), message.getText(), result);
                        return result;
                    });
                })
                .switchIfEmpty(Mono.just(commandChatNotFound));
    }

    private String formatAlert(AlertDBO alert) {
        return String.format("%s/%s %s [ID: %s]",
                alert.getBaseCurrency(),
                alert.getTargetCurrency(),
                alert.getExpr(),
                alert.getId());
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String request, String result) {
        historyService.saveHistory(
                chatId,
                "ALERT_LIST",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}
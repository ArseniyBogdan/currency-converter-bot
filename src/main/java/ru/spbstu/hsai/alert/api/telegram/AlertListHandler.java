package ru.spbstu.hsai.alert.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

@Component
@RequiredArgsConstructor
public class AlertListHandler implements CommandHandler {

    private final AlertService alertService;

    @Override
    @BotCommand("/alert_list")
    public Mono<String> handle(Message message) {
        return alertService.getAllAlertsByChatId(message.getChatId())
                .flatMap(alerts -> {
                    if (alerts.isEmpty()) {
                        return Mono.just("ℹ️ Активных уведомлений нет");
                    }

                    StringBuilder sb = new StringBuilder("📋 Список активных уведомлений:\n\n");
                    for (int i = 0; i < alerts.size(); i++) {
                        AlertDBO alert = alerts.get(i);
                        sb.append(i + 1).append(". ")
                                .append(formatAlert(alert))
                                .append("\n");
                    }
                    return Mono.just(sb.toString());
                })
                .switchIfEmpty(Mono.just("❌ Чат не найден"));
    }

    private String formatAlert(AlertDBO alert) {
        return String.format("%s/%s %s [ID: %s]",
                alert.getBaseCurrency(),
                alert.getTargetCurrency(),
                alert.getExpr(),
                alert.getId());
    }
}
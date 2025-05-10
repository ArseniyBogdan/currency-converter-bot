package ru.spbstu.hsai.alert.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

@Component
@RequiredArgsConstructor
public class AlertRemoveHandler implements CommandHandler {

    private final AlertService alertService;

    @Override
    @BotCommand("/alert_remove")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    String[] parts = commandText.split("\\s+");
                    if (parts.length < 2) {
                        return Mono.error(new CCBException("❌ Укажите ID уведомления"));
                    }

                    return alertService.deleteAlert(parts[1], message.getChatId());
                })
                .onErrorResume(e -> Mono.just(e.getMessage()));
    }
}
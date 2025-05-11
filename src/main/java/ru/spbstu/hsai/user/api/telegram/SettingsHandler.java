package ru.spbstu.hsai.user.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.service.UserServiceImpl;

import java.util.Map;

/**
 * Обработчик команды /currencies для получения списка доступных валют
 */
@Component
@RequiredArgsConstructor
public class SettingsHandler implements CommandHandler {
    private final UserServiceImpl userService;
    private final HistorySDK historySDK;

    @Value("${command.settings}")
    private String commandSettingsReply;

    /**
     * Обрабатывает команду /settings, возвращая настройки пользователя
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с настройками пользователя
     */
    @Override
    @BotCommand("/settings")
    public Mono<String> handle(Message message) {
        return userService.getUserSettings(message.getChatId())
                .flatMap(settings -> {
                    String response = String.format(
                            commandSettingsReply,
                            settings.getHomeCurrency(),
                            settings.getDefaultPair()
                    );
                    return Mono.just(response).map(result -> {
                        saveHistory(message.getChatId(), message.getText(), result);
                        return result;
                    });
                });
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String request, String result) {
        historySDK.saveHistory(
                chatId,
                "SETTINGS",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}

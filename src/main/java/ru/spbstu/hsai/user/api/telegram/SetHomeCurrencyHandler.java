package ru.spbstu.hsai.user.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.service.UserServiceImpl;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик команды /currencies для получения списка доступных валют
 */
@Component
@RequiredArgsConstructor
public class SetHomeCurrencyHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/sethome\\s+([A-Z]{3})$", Pattern.CASE_INSENSITIVE);

    private final UserServiceImpl userService;
    private final HistorySDK historySDK;

    @Value("${command.sethome.success}")
    private String commandSetHomeCurrencyReply;

    @Value("${command.sethome.error}")
    private String commandFormat;

    /**
     * Обрабатывает команду /sethome, производя установку домашней валюты
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом установки домашней валюты или сообщением об ошибке
     */
    @Override
    @BotCommand("/sethome")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());

                    // Проверка формата команды
                    if (!matcher.matches()) {
                        return Mono.error(new CCBException(commandFormat));
                    }

                    String currency = matcher.group(1).toUpperCase();

                    // Проверка существования валюты
                    return userService.setHomeCurrency(message.getChatId(), currency).thenReturn(commandSetHomeCurrencyReply).map(result -> {
                        saveHistory(message.getChatId(), commandText, result);
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
                "SET_HOME",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}

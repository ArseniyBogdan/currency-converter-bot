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
public class SetPairHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/setpair\\s+([A-Z]{3})/([A-Z]{3})$", Pattern.CASE_INSENSITIVE);

    private final HistorySDK historySDK;

    private final UserServiceImpl userService;

    @Value("${command.setpair.success}")
    private String commandSetHomeCurrencyReply;

    @Value("${command.setpair.error.format}")
    private String errorFormat;

    @Value("${command.setpair.error.equals}")
    private String errorEquals;

    /**
     * Обрабатывает команду /setpair, производя установку валютной пары по умолчанию
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом установки валютной пары или сообщением об ошибке
     */
    @Override
    @BotCommand("/setpair")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());

                    // Проверка формата команды
                    if (!matcher.matches()) {
                        return Mono.error(new CCBException(errorFormat));
                    }

                    String currencyBase = matcher.group(1).toUpperCase();
                    String currencyTarget = matcher.group(2).toUpperCase();

                    // Проверка на одинаковые валюты
                    if (currencyBase.equals(currencyTarget)) {
                        return Mono.error(new CCBException(errorEquals));
                    }

                    // Проверка существования валюты
                    return userService.setPair(message.getChatId(), currencyBase, currencyTarget).thenReturn(commandSetHomeCurrencyReply).map(result -> {
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
                "SET_PAIR",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}

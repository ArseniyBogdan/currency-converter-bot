package ru.spbstu.hsai.alert.api.telegram;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.RatesService;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик команды /math_help для создания уведомления
 */
@Component
@RequiredArgsConstructor
public class AlertAddHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile(
                    "^/alert_add\\s+([A-Z]{3}/[A-Z]{3})\\s+(.+)$", // Учтена команда в начале
                    Pattern.CASE_INSENSITIVE
            );

    private final AlertService alertService;
    private final RatesService ratesService;
    private final HistorySDK historyService;

    @Value("${command.alert.success}")
    private String commandAlertSuccess;

    @Value("${command.alert.error}")
    private String commandAlertError;

    @Value("${command.alert.error.format}")
    private String commandFormatError;

    @Value("${command.alert.error.pair}")
    private String commandPairNotFound;

    /**
     * Обрабатывает команду /alert_add, производя создание уведомления
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом создания уведомления или сообщением об ошибке
     */
    @Override
    @BotCommand("/alert_add")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());
                    if (!matcher.find()) {
                        return Mono.error(new CCBException(
                                commandFormatError
                        ));
                    }

                    String pair = matcher.group(1).toUpperCase();
                    String condition = matcher.group(2).trim();

                    return validatePair(pair).then(alertService.addAlert(message.getChatId(), pair, condition))
                            .then(Mono.just(commandAlertSuccess))
                            .map(result -> {
                                saveHistory(message.getChatId(), pair, commandText, result);
                                return result;
                            });
                })
                .onErrorResume(e -> Mono.just(
                        e instanceof CCBException ? e.getMessage() : commandAlertError
                ));
    }

    private Mono<Void> validatePair(String pair) {
        String[] currencies = pair.split("/");
        return ratesService.getCurrencyPairId(currencies[0], currencies[1]).switchIfEmpty(Mono.error(new CCBException(
                commandPairNotFound
        ))).then();
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String currencyCode, String request, String result) {
        historyService.saveHistory(
                chatId,
                "ALERT_ADD",
                currencyCode,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}
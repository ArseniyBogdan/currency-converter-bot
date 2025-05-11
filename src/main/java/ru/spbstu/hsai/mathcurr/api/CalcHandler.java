package ru.spbstu.hsai.mathcurr.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.mathcurr.service.MathCurrService;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик команды /calc для вычисления выражений с валютами
 */
@Component
@RequiredArgsConstructor
public class CalcHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/calc\\s+(.+?)\\s+to\\s+([A-Z]{3})$", Pattern.CASE_INSENSITIVE);

    private final MathCurrService mathService;
    private final HistorySDK historySDK;

    @Value("${command.calc.success}")
    private String successTemplate;

    @Value("${command.calc.error}")
    private String errorMessage;

    @Value("${command.calc.error.format}")
    private String errorFormat;

    /**
     * Обрабатывает команду /calc, производя вычисление выражения с валютами
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом вычисления выражения или сообщением об ошибке
     */
    @Override
    @BotCommand("/calc")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());

                    if (!matcher.find()) {
                        return Mono.error(new CCBException(errorFormat));
                    }

                    String expression = matcher.group(1);
                    String targetCurrency = matcher.group(2).toUpperCase();

                    return mathService.processCalculation(message.getChatId(), expression, targetCurrency)
                            .map(result -> String.format(successTemplate, result, targetCurrency)).map(result -> {
                                saveHistory(message.getChatId(), targetCurrency, commandText, result);
                                return result;
                            });
                })
                .onErrorResume(e -> Mono.just(
                        e instanceof CCBException ? e.getMessage() : errorMessage
                ));
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String currencyCode, String request, String result) {
        historySDK.saveHistory(
                chatId,
                "CALC",
                currencyCode,
                Map.of("request", request, "result", result)
        ).subscribe();
    }

}
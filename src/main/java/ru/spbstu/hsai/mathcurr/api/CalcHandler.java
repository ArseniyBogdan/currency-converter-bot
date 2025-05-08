package ru.spbstu.hsai.mathcurr.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.mathcurr.service.MathCurrService;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CalcHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/calc\\s+(.+?)\\s+to\\s+([A-Z]{3})$", Pattern.CASE_INSENSITIVE);

    private final MathCurrService mathService;

    @Value("${command.calc.success}")
    private String successTemplate;

    @Value("${command.calc.error}")
    private String errorMessage;

    @Override
    @BotCommand("/calc")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());

                    if (!matcher.find()) {
                        return Mono.error(new CCBException(
                                "❌ Неверный формат команды. Используйте:\n" +
                                        "<code>/calc &lt;выражение&gt; to &lt;ВАЛЮТА&gt</code>;\n" +
                                        "Пример: <code>/calc 100 USD + 50 EUR * 2 to RUB</code>"
                        ));
                    }

                    String expression = matcher.group(1);
                    String targetCurrency = matcher.group(2).toUpperCase();

                    return mathService.processCalculation(message.getChatId(), expression, targetCurrency)
                            .map(result -> String.format(successTemplate, result, targetCurrency));
                })
                .onErrorResume(e -> Mono.just(
                        e instanceof CCBException ? e.getMessage() : errorMessage
                ));
    }

}
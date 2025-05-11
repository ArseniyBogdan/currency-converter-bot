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
import ru.spbstu.hsai.user.RatesService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Override
    @BotCommand("/alert_add")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());
                    if (!matcher.find()) {
                        return Mono.error(new CCBException(
                                "❌ Неверный формат команды. Пример: <code>/alert_add USD/RUB > 90</code>"
                        ));
                    }

                    String pair = matcher.group(1).toUpperCase();
                    String condition = matcher.group(2).trim();

                    return validatePair(pair).then(alertService.addAlert(message.getChatId(), pair, condition)).then(Mono.just(commandAlertSuccess));
                })
                .onErrorResume(e -> Mono.just(
                        e instanceof CCBException ? e.getMessage() : commandAlertError
                ));
    }

    private Mono<Void> validatePair(String pair) {
        String[] currencies = pair.split("/");
        return ratesService.getCurrencyPairId(currencies[0], currencies[1]).switchIfEmpty(Mono.error(new CCBException(
                "❌ Такой валютной пары не существует. \nСписок поддерживаемых валют можно\n посмотреть командой /currencies"
        ))).then();
    }
}
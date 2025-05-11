package ru.spbstu.hsai.rates.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.UserDTO;
import ru.spbstu.hsai.user.UserServiceSDK;
import ru.spbstu.hsai.user.UserSettings;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ConvertHandler implements CommandHandler {
    private static final Pattern CONVERT_PATTERN =
            Pattern.compile("^/convert\\s+(\\d+(?:\\.\\d+)?)(?:\\s+([A-Z]{3})(?:\\s+([A-Z]{3}))?)?$", Pattern.CASE_INSENSITIVE);
    private final RatesServiceImpl ratesService;
    private final UserServiceSDK userService;
    private final HistorySDK historySDK;

    @Value("${command.convert.success}")
    private String successTemplate;

    @Value("${command.convert.error}")
    private String errorMessage;

    @Override
    @BotCommand("/convert")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = CONVERT_PATTERN.matcher(commandText.trim());

                    if (!matcher.find()) {
                        return Mono.error(new CCBException(
                                "❌ Неверный формат команды. Используйте:\n" +
                                        "<code>/convert &lt;СУММА&gt; &lt;ИЗ&gt; &lt;В&gt;</code>\n" +
                                        "<code>/convert &lt;СУММА&gt; &lt;ИЗ&gt;</code>\n" +
                                        "<code>/convert &lt;СУММА&gt;</code>"
                        ));
                    }

                    // Парсим параметры
                    double amount = Double.parseDouble(matcher.group(1));
                    String fromCurrency = matcher.group(2) != null ? matcher.group(2).toUpperCase() : null;
                    String toCurrency = matcher.group(3) != null ? matcher.group(3).toUpperCase() : null;

                    return processConvertRequest(
                            message.getChatId(),
                            amount,
                            fromCurrency,
                            toCurrency
                    )// Сохраняем историю после успешного выполнения
                    .flatMap(conversionResult -> {
                        Map<String, Object> historyPayload = new HashMap<>();
                        historyPayload.put("original_command", commandText);
                        historyPayload.put("amount", amount);
                        historyPayload.put("from", fromCurrency);
                        historyPayload.put("to", toCurrency);
                        historyPayload.put("result", conversionResult);

                        return historySDK.saveHistory(
                                message.getChatId(),
                                "CONVERT",
                                fromCurrency + "/" + toCurrency,
                                historyPayload
                        ).thenReturn(conversionResult);
                    });
                })
                .onErrorResume(e -> Mono.just(
                        e instanceof CCBException ? e.getMessage() : errorMessage
                ));
    }

    private Mono<String> processConvertRequest(Long chatId, Double amount, String from, String to) {
        return userService.getUserByChatId(chatId)
                .flatMap(user -> determineCurrencies(user.getSettings(), from, to))
                .flatMap(currencies -> convertAmount(amount, currencies.getT1(), currencies.getT2()))
                .switchIfEmpty(Mono.error(new CCBException("❌ Настройки пользователя не найдены")));
    }

    private Mono<Tuple2<String, String>> determineCurrencies(UserSettings settings, String from, String to) {
        // Все валюты указаны явно
        if (from != null && to != null) {
            return Mono.just(Tuples.of(from.toUpperCase(), to.toUpperCase()));
        }

        // Только исходная валюта (целевая - домашняя)
        if (from != null) {
            if (settings.getHomeCurrency() == null) {
                return Mono.error(new CCBException("❌ Домашняя валюта не установлена"));
            }
            return Mono.just(Tuples.of(from.toUpperCase(), settings.getHomeCurrency()));
        }

        // Используем пару по умолчанию
        if (settings.getDefaultPair() == null) {
            return Mono.error(new CCBException("❌ Пара по умолчанию не установлена"));
        }
        String[] pair = settings.getDefaultPair().split("/");
        return Mono.just(Tuples.of(pair[0], pair[1]));
    }

    private Mono<String> convertAmount(Double amount, String from, String to) {
        return ratesService.getExchangeRate(from, to)
                .flatMap(rate -> {
                    double result = amount * rate.getCurrentRate().doubleValue();
                    return Mono.just(String.format(successTemplate,
                            String.format("%.2f", amount),
                            from,
                            String.format("%.2f", result),
                            to,
                            from,
                            String.format("%.6f", rate.getCurrentRate()),
                            to,
                            rate.getUpdated().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                    ));
                })
                .switchIfEmpty(Mono.error(new CCBException("❌ Курс для пары " + from + "/" + to + " не найден")));
    }
}
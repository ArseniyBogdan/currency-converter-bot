package ru.spbstu.hsai.rates.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.UserServiceSDK;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обработчик команды /rate для получения текущего курса валюты или валютной пары
 */
@Component
@RequiredArgsConstructor
public class RateHandler implements CommandHandler {
    private static final Pattern RATE_PATTERN =
            Pattern.compile("^/rate(?:\\s+(([A-Z]{3})(?:/([A-Z]{3}))?))?$", Pattern.CASE_INSENSITIVE);
    private final RatesServiceImpl ratesService;
    private final UserServiceSDK userService;
    private final HistorySDK historySDK;

    @Value("${command.rate.success}")
    private String commandRatesReply;

    @Value("${command.rate.error}")
    private String errorMessage;

    @Value("${command.rate.error.format}")
    private String errorFormat;

    @Value("${command.rate.error.pair}")
    private String errorPairNotSpecified;

    @Value("${command.rate.error.settings}")
    private String errorSettingsNotFound;

    @Value("${command.rate.error.currency}")
    private String errorCurrencyNotSpecified;

    @Value("${command.rate.error.rate}")
    private String errorRateNotFound;

    /**
     * Обрабатывает команду /rate для получения курса валюты или валютной пары
     * @param message входящее сообщение с параметрами команды
     * @return форматированный ответ с курсом или сообщение об ошибке
     */
    @Override
    @BotCommand("/rate")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = RATE_PATTERN.matcher(commandText.trim());

                    if (!matcher.find()) {
                        return Mono.error(new CCBException(errorFormat));
                    }

                    return processRateRequest(message.getChatId(), matcher).map(result -> {
                        String currency1 = matcher.group(2);
                        String currency2 = matcher.group(3);
                        if (currency1 != null && currency2 != null){
                            saveHistory(message.getChatId(), currency1 + "/" + currency2, commandText, result);
                        }
                        else if (currency2 != null){
                            saveHistory(message.getChatId(), currency2, commandText, result);
                        }
                        else{
                            saveHistory(message.getChatId(), currency1, commandText, result);
                        }
                        return result;
                    });
                })
                .onErrorResume(e -> Mono.just(
                        e instanceof CCBException ? e.getMessage() : errorMessage
                ));
    }

    private Mono<String> processRateRequest(Long chatId, Matcher matcher) {
        String currency1 = matcher.group(2);
        String currency2 = matcher.group(3);

        // Определение типа запроса
        if (currency1 == null) {
            return handleDefaultPair(chatId);
        } else if (currency2 == null) {
            return handleSingleCurrency(chatId, currency1);
        } else {
            return handleCurrencyPair(currency1, currency2);
        }
    }

    private Mono<String> handleDefaultPair(Long chatId) {
        return userService.getUserByChatId(chatId)
                .flatMap(user -> {
                    if (user.getSettings().getDefaultPair() == null) {
                        return Mono.error(new CCBException(errorPairNotSpecified));
                    }
                    return processPair(
                            user.getSettings().getDefaultPair().split("/")[0],
                            user.getSettings().getDefaultPair().split("/")[1]
                    );
                })
                .switchIfEmpty(Mono.error(new CCBException(errorSettingsNotFound)));
    }

    private Mono<String> handleSingleCurrency(Long chatId, String currency) {
        return userService.getUserByChatId(chatId)
                .flatMap(user -> {
                    if (user.getSettings().getHomeCurrency() == null) {
                        return Mono.error(new CCBException(
                                errorCurrencyNotSpecified
                        ));
                    }
                    return processPair(user.getSettings().getHomeCurrency(), currency);
                })
                .switchIfEmpty(Mono.error(new CCBException(errorSettingsNotFound)));
    }

    private Mono<String> handleCurrencyPair(String currency1, String currency2) {
        return processPair(currency1.toUpperCase(), currency2.toUpperCase());
    }

    private Mono<String> processPair(String baseCurrency, String targetCurrency) {
        String pair = (baseCurrency + "/" + targetCurrency).toUpperCase();
        return ratesService.getExchangeRate(baseCurrency, targetCurrency)
                .flatMap(rate -> {
                    String formattedRate = String.format("%.4f", rate.getCurrentRate());
                    return Mono.just(String.format(commandRatesReply,
                            pair,
                            formattedRate,
                            rate.getUpdated().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                    ));
                })
                .switchIfEmpty(Mono.error(new CCBException(String.format(errorRateNotFound,pair))));
    }

    private void saveHistory(Long chatId, String currencyCode, String request, String result) {
        historySDK.saveHistory(
                chatId,
                "RATE",
                currencyCode,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}

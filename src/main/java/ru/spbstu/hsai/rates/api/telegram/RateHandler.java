package ru.spbstu.hsai.rates.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.UserServiceSDK;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RateHandler implements CommandHandler {
    private static final Pattern RATE_PATTERN =
            Pattern.compile("^/rate(?:\\s+(([A-Z]{3})(?:/([A-Z]{3}))?))?$", Pattern.CASE_INSENSITIVE);
    private final RatesServiceImpl ratesService;
    private final UserServiceSDK userService;

    @Value("${command.rate.success}")
    private String commandRatesReply;

    @Value("${command.rate.error}")
    private String errorMessage;

    @Override
    @BotCommand("/rate")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = RATE_PATTERN.matcher(commandText.trim());

                    if (!matcher.find()) {
                        return Mono.error(new CCBException(
                                "❌ Неверный формат команды. Используйте:\n" +
                                        "<code>/rate [ВАЛЮТА1]/[ВАЛЮТА2]</code>\n" +
                                        "<code>/rate [ВАЛЮТА]</code>\n" +
                                        "<code>/rate</code>"
                        ));
                    }

                    return processRateRequest(message.getChatId(), matcher);
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
                        return Mono.error(new CCBException(
                                "❌ Пара по умолчанию не установлена\n" +
                                        "Используйте /setpair USD/EUR"
                        ));
                    }
                    return processPair(
                            user.getSettings().getDefaultPair().split("/")[0],
                            user.getSettings().getDefaultPair().split("/")[1]
                    );
                })
                .switchIfEmpty(Mono.error(new CCBException("❌ Настройки пользователя не найдены")));
    }

    private Mono<String> handleSingleCurrency(Long chatId, String currency) {
        return userService.getUserByChatId(chatId)
                .flatMap(user -> {
                    if (user.getSettings().getHomeCurrency() == null) {
                        return Mono.error(new CCBException(
                                "❌ Домашняя валюта не установлена\n" +
                                        "Используйте /sethome RUB"
                        ));
                    }
                    return processPair(user.getSettings().getHomeCurrency(), currency);
                })
                .switchIfEmpty(Mono.error(new CCBException("❌ Настройки пользователя не найдены")));
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
                .switchIfEmpty(Mono.error(new CCBException("❌ Курс для пары " + pair + " не найден")));
    }
}

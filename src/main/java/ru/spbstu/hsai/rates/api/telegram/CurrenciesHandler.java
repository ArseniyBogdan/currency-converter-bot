package ru.spbstu.hsai.rates.api.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.List;
import java.util.Map;

/**
 * Обработчик команды /currencies для получения списка доступных валют
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrenciesHandler implements CommandHandler {
    private final RatesServiceImpl ratesService;
    private final HistorySDK historySDK;

    @Value("${command.currencies}")
    private String commandCurrenciesReply;

    @Value("${command.currencies.error}")
    private String errorMessage;

    /**
     * Обрабатывает команду /currencies, возвращая форматированный список валют
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с форматированным списком валют или сообщением об ошибке
     */
    @Override
    @BotCommand("/currencies")
    public Mono<String> handle(Message message) {
        return processCurrenciesRequest(message.getChatId())
                .doOnSuccess(result -> {
                    log.debug("Currencies list generated for chat {}", message.getChatId());
                    saveHistory(message.getChatId(), message.getText(), result);
                })
                .onErrorResume(e -> {
                    log.error("Error processing currencies command", e);
                    return Mono.just(e instanceof CCBException ? e.getMessage() : errorMessage);
                });
    }

    /**
     * Основная логика обработки запроса
     */
    private Mono<String> processCurrenciesRequest(Long chatId) {
        return ratesService.getAllCurrencies()
                .collectList()
                .flatMap(currencies -> {
                    if (currencies.isEmpty()) {
                        return Mono.error(new CCBException(errorMessage));
                    }

                    return Mono.just(formatCurrenciesList(currencies));
                });
    }

    /**
     * Форматирует список валют для ответа пользователю
     */
    private String formatCurrenciesList(List<CurrencyDBO> currencies) {
        StringBuilder sb = new StringBuilder();
        int counter = 0;

        for (CurrencyDBO currency : currencies) {
            if (counter % 5 == 0 && counter != 0) {
                sb.append("\n");
            }
            sb.append(currency.getCode())
                    .append(" (")
                    .append(currency.getName())
                    .append("), ");
            counter++;
        }

        // Убираем последнюю запятую и пробел
        return !sb.isEmpty()
                ? sb.substring(0, sb.length() - 2)
                : "";
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String request, String result) {
        historySDK.saveHistory(
                chatId,
                "CURRENCIES",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}

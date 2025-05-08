package ru.spbstu.hsai.rates.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CurrenciesHandler implements CommandHandler {
    private final RatesServiceImpl ratesService;

    @Value("${command.currencies}")
    private String commandCurrenciesReply;

    @Value("${command.currencies.error}")
    private String errorMessage;

    @Override
    @BotCommand("/currencies")
    public Mono<String> handle(Message message) {
        return ratesService.getAllCurrencies()
                .collectList()
                .flatMap(currencies -> {
                    if(currencies.isEmpty()){
                        return Mono.error(new CCBException(errorMessage));
                    }
                    String formattedCurrencies = formatCurrenciesList(currencies);
                    return Mono.just(String.format(commandCurrenciesReply, formattedCurrencies));
                });
    }

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
}

package ru.spbstu.hsai.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.dao.AlertDAO;
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.alert.entities.AlertResult;
import ru.spbstu.hsai.alert.entities.RateChange;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatesUpdateService {
    private final AlertDAO alertDAO;

    public Flux<AlertResult> processRateChange(RateChange event) {
        return alertDAO.findByBaseCurrencyAndTargetCurrency(
                        event.getBaseCurrency(),
                        event.getTargetCurrency()
                )
                .flatMap(alert -> checkAlertConditions(alert, event));
    }

    private Mono<AlertResult> checkAlertConditions(AlertDBO alert, RateChange event) {
        return Mono.fromCallable(() -> {
            String condition = alert.getExpr();
            boolean shouldNotify = false;
            String reason = "";

            if (condition.contains("%")) {
                // Обработка относительных изменений
                BigDecimal threshold = new BigDecimal(condition.replaceAll("[^0-9.-]", ""));
                BigDecimal actualChange = event.getChangePercent();

                if (condition.startsWith("+") && actualChange.compareTo(threshold) >= 0) {
                    shouldNotify = true;
                    reason = String.format("Рост на %.2f%%", actualChange);
                } else if (condition.startsWith("-") && actualChange.compareTo(threshold.abs().negate()) <= 0) {
                    shouldNotify = true;
                    reason = String.format("Падение на %.2f%%", actualChange.abs());
                }
            } else {
                // Обработка абсолютных значений
                BigDecimal threshold = new BigDecimal(condition.replaceAll("[^0-9.]", ""));
                int comparison = event.getNewRate().compareTo(threshold);

                if (condition.contains(">") && comparison > 0) {
                    shouldNotify = true;
                    reason = String.format("Курс превысил %s", threshold);
                } else if (condition.contains("<") && comparison < 0) {
                    shouldNotify = true;
                    reason = String.format("Курс упал ниже %s", threshold);
                }
            }

            if (shouldNotify) {
                return new AlertResult(alert.getChatId(), event.getBaseCurrency(), event.getTargetCurrency(),
                        event.getNewRate(), event.getChangePercent(), reason);
            }
            else{
                return null;
            }
        });
    }
}

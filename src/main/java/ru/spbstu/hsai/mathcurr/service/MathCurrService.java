package ru.spbstu.hsai.mathcurr.service;

import lombok.RequiredArgsConstructor;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.mathcurr.RatesForMathService;
import ru.spbstu.hsai.user.UserServiceSDK;
import ru.spbstu.hsai.user.UserSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MathCurrService {
    private static final Pattern CURRENCY_TERM_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s+([A-Z]{3})");

    private final RatesForMathService ratesService;
    private final UserServiceSDK userService;

    public Mono<Double> processCalculation(Long chatId, String expression, String targetCurrency) {
        return userService.getUserByChatId(chatId)
                .flatMap(settings -> convertExpression(expression, targetCurrency, settings.getSettings()))
                .flatMap(this::evaluateExpression)
                .switchIfEmpty(Mono.error(new CCBException("❌ Не удалось вычислить выражение")));
    }

    private Mono<String> convertExpression(String expression, String targetCurrency, UserSettings settings) {
        return Flux.fromIterable(getCurrencyTerms(expression))
                .flatMap(term -> convertTerm(term, targetCurrency, settings))
                .collectMap(CurrencyTerm::original, CurrencyTerm::converted)
                .map(cache -> replaceTermsInExpression(expression, cache));
    }

    private List<CurrencyTerm> getCurrencyTerms(String expression) {
        List<CurrencyTerm> terms = new ArrayList<>();
        Matcher matcher = CURRENCY_TERM_PATTERN.matcher(expression);

        while (matcher.find()) {
            terms.add(new CurrencyTerm(
                    matcher.group(0),
                    Double.parseDouble(matcher.group(1)),
                    matcher.group(2)
            ));
        }
        return terms;
    }

    private Mono<CurrencyTerm> convertTerm(CurrencyTerm term, String targetCurrency, UserSettings settings) {
        return ratesService.getBigDecimalExchangeRate(term.currency(), targetCurrency)
                .map(rate -> term.convert(rate.doubleValue()))
                .onErrorResume(e -> ratesService.getBigDecimalExchangeRate(targetCurrency, term.currency())
                        .map(inverseRate -> term.convert(1 / inverseRate.doubleValue())))
                .switchIfEmpty(Mono.error(new CCBException(
                        "❌ Не удалось получить курс для " + term.currency() + "/" + targetCurrency
                )));
    }

    private String replaceTermsInExpression(String expression, Map<String, String> cache) {
        String convertedExpression = expression;
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            convertedExpression = convertedExpression.replace(entry.getKey(), entry.getValue());
        }
        return convertedExpression;
    }

    private Mono<Double> evaluateExpression(String convertedExpression) {
        return Mono.fromCallable(() -> {
            Expression exp = new ExpressionBuilder(convertedExpression).build();
            return exp.evaluate();
        }).onErrorResume(e -> Mono.error(new CCBException(
                "❌ Ошибка вычисления выражения: " + e.getMessage()
        )));
    }

    private record CurrencyTerm(
            String original,
            double amount,
            String currency,
            String converted
    ) {
        public CurrencyTerm(String original, double amount, String currency) {
            this(original, amount, currency, null);
        }

        public CurrencyTerm convert(double rate) {
            return new CurrencyTerm(
                    original,
                    amount,
                    currency,
                    String.valueOf(amount * rate)
            );
        }
    }

}

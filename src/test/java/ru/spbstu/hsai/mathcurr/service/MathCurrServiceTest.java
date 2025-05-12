package ru.spbstu.hsai.mathcurr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.mathcurr.RatesForMathService;
import ru.spbstu.hsai.user.UserDTO;
import ru.spbstu.hsai.user.UserServiceSDK;
import ru.spbstu.hsai.user.UserSettings;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MathCurrServiceTest {

    @Mock
    private RatesForMathService ratesService;

    @Mock
    private UserServiceSDK userService;

    @InjectMocks
    private MathCurrService mathCurrService;

    // Класс-обертка для доступа к CurrencyTerm через рефлексию
    private static class CurrencyTermWrapper {
        private final Object termInstance;

        public CurrencyTermWrapper(Object termInstance) {
            this.termInstance = termInstance;
        }

        public String getOriginal() {
            return (String) ReflectionTestUtils.getField(termInstance, "original");
        }

        public double getAmount() {
            return (double) ReflectionTestUtils.getField(termInstance, "amount");
        }

        public String getCurrency() {
            return (String) ReflectionTestUtils.getField(termInstance, "currency");
        }

        public String getConverted() {
            return (String) ReflectionTestUtils.getField(termInstance, "converted");
        }
    }

    @Test
    void processCalculation_Success() {
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        UserDTO user = new UserDTO(12345L, LocalDateTime.now(), settings);
        String expression = "100 USD + 200 USD";
        String targetCurrency = "EUR";

        when(userService.getUserByChatId(anyLong()))
                .thenReturn(Mono.just(user));
        when(ratesService.getBigDecimalExchangeRate(eq("USD"), eq("EUR")))
                .thenReturn(Mono.just(BigDecimal.valueOf(0.85)));

        StepVerifier.create(mathCurrService.processCalculation(1L, expression, targetCurrency))
                .expectNext(300 * 0.85)
                .verifyComplete();
    }

    @Test
    void processCalculation_CurrencyConversionError() {
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        UserDTO user = new UserDTO(12345L, LocalDateTime.now(), settings);
        String expression = "100 JPY + 200 USD";
        String targetCurrency = "EUR";

        when(userService.getUserByChatId(anyLong()))
                .thenReturn(Mono.just(user));
        when(ratesService.getBigDecimalExchangeRate(eq("JPY"), eq("EUR")))
                .thenReturn(Mono.error(new CCBException(null)));
        when(ratesService.getBigDecimalExchangeRate(eq("EUR"), eq("JPY")))
                .thenReturn(Mono.error(new CCBException(null)));

        StepVerifier.create(mathCurrService.processCalculation(1L, expression, targetCurrency))
                .expectErrorMatches(ex -> ex instanceof CCBException)
                .verify();
    }

    @Test
    void getCurrencyTerms_ShouldParseCorrectly() throws Exception {
        // Получаем доступ к приватному методу через рефлексию
        Method method = MathCurrService.class.getDeclaredMethod("getCurrencyTerms", String.class);
        method.setAccessible(true);

        String expression = "100.5 USD + 200.75 EUR - 300 GBP";
        List<?> terms = (List<?>) method.invoke(mathCurrService, expression);

        assertEquals(3, terms.size());

        // Проверяем поля через рефлексию
        CurrencyTermWrapper term1 = new CurrencyTermWrapper(terms.get(0));
        assertEquals("100.5 USD", term1.getOriginal());
        assertEquals(100.5, term1.getAmount(), 0.1);
        assertEquals("USD", term1.getCurrency());

        CurrencyTermWrapper term2 = new CurrencyTermWrapper(terms.get(1));
        assertEquals("200.75 EUR", term2.getOriginal());
        assertEquals(200.75, term2.getAmount(), 0.1);
        assertEquals("EUR", term2.getCurrency());
    }

    @Test
    void convertTerm_WithDirectRate() throws Exception {
        // Создаем экземпляр CurrencyTerm через рефлексию
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        Class<?> termClass = Class.forName("ru.spbstu.hsai.mathcurr.service.MathCurrService$CurrencyTerm");
        Object term = termClass.getDeclaredConstructors()[0].newInstance("100 USD", 100.0, "USD");

        when(ratesService.getBigDecimalExchangeRate(eq("USD"), eq("EUR")))
                .thenReturn(Mono.just(BigDecimal.valueOf(0.85)));

        // Вызываем приватный метод
        Method method = MathCurrService.class.getDeclaredMethod("convertTerm",
                termClass, String.class, UserSettings.class);
        method.setAccessible(true);

        Mono<?> resultMono = (Mono<?>) method.invoke(
                mathCurrService, term, "EUR", settings
        );

        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    CurrencyTermWrapper converted = new CurrencyTermWrapper(result);
                    assertEquals("85.0", converted.getConverted());
                })
                .verifyComplete();
    }

    @Test
    void replaceTermsInExpression_ShouldReplaceAllTerms() throws Exception {
        Method method = MathCurrService.class.getDeclaredMethod(
                "replaceTermsInExpression", String.class, Map.class
        );
        method.setAccessible(true);

        String expression = "100 USD + 200 EUR";
        Map<String, String> cache = Map.of(
                "100 USD", "85.0",
                "200 EUR", "220.0"
        );

        String result = (String) method.invoke(mathCurrService, expression, cache);
        assertEquals("85.0 + 220.0", result);
    }

    @Test
    void evaluateExpression_ComplexExpression() throws Exception {
        Method method = MathCurrService.class.getDeclaredMethod("evaluateExpression", String.class);
        method.setAccessible(true);

        String expression = "(2 + 3) * 4 - 5";
        Mono<Double> resultMono = (Mono<Double>) method.invoke(mathCurrService, expression);

        StepVerifier.create(resultMono)
                .expectNext(15.0)
                .verifyComplete();
    }
}
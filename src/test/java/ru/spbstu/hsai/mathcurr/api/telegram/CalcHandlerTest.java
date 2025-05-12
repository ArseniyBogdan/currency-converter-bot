package ru.spbstu.hsai.mathcurr.api.telegram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.mathcurr.api.CalcHandler;
import ru.spbstu.hsai.mathcurr.service.MathCurrService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CalcHandlerTest.TestConfig.class)
public class CalcHandlerTest {

    @Configuration
    @PropertySource("classpath:command.properties")
    static class TestConfig {
        @Bean
        public MathCurrService mathCurrService() {
            return mock(MathCurrService.class);
        }

        @Bean
        public HistorySDK historySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public CalcHandler calcHandler(MathCurrService mathService, HistorySDK historySDK) {
            return new CalcHandler(mathService, historySDK);
        }
    }

    @Autowired
    private MathCurrService mathService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private CalcHandler calcHandler;

    @Value("${command.calc.success}")
    private String successTemplate;

    @Value("${command.calc.error}")
    private String errorMessage;

    @Value("${command.calc.error.format}")
    private String errorFormat;

    private final Long chatId = 12345L;

    @AfterEach
    void tearDown() {
        reset(mathService, historySDK);
    }

    @Test
    void handleValidCommandShouldReturnFormattedResult() {
        // Setup
        Message message = createMessage("/calc 100 USD to EUR");
        when(mathService.processCalculation(eq(chatId), eq("100 USD"), eq("EUR")))
                .thenReturn(Mono.just(150.0));
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());

        // Test & Verify
        StepVerifier.create(calcHandler.handle(message))
                .expectNext(String.format(successTemplate, 150.0, "EUR"))
                .verifyComplete();

        verify(mathService).processCalculation(chatId, "100 USD", "EUR");
        verifyHistorySaved("EUR", "/calc 100 USD to EUR", String.format(successTemplate, 150.0, "EUR"));
    }

    @Test
    void handleInvalidFormatShouldReturnFormatError() {
        // Setup
        Message message = createMessage("/calc invalid_command");

        // Test & Verify
        StepVerifier.create(calcHandler.handle(message))
                .expectNext(errorFormat)
                .verifyComplete();

        verifyNoInteractions(mathService);
    }

    @Test
    void handleServiceErrorShouldReturnGenericError() {
        // Setup
        Message message = createMessage("/calc 100 USD to EUR");
        when(mathService.processCalculation(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        // Test & Verify
        StepVerifier.create(calcHandler.handle(message))
                .expectNext(errorMessage)
                .verifyComplete();
    }

    @Test
    void handleCCBExceptionShouldReturnSpecificError() {
        // Setup
        Message message = createMessage("/calc 100 USD to EUR");
        String expectedError = "Currency not supported";
        when(mathService.processCalculation(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.error(new CCBException(expectedError)));

        // Test & Verify
        StepVerifier.create(calcHandler.handle(message))
                .expectNext(expectedError)
                .verifyComplete();
    }

    @Test
    void shouldUpperCaseTargetCurrency() {
        // Setup
        Message message = createMessage("/calc 100 USD to eur");
        when(mathService.processCalculation(eq(chatId), anyString(), eq("EUR")))
                .thenReturn(Mono.just(100.0));
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());

        // Test & Verify
        StepVerifier.create(calcHandler.handle(message))
                .expectNext(String.format(successTemplate, 100.0, "EUR"))
                .verifyComplete();
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String currency, String request, String result) {
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(historySDK).saveHistory(
                eq(chatId),
                eq("CALC"),
                eq(currency),
                payloadCaptor.capture()
        );

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(request, payload.get("request"));
        assertEquals(result, payload.get("result"));
    }
}
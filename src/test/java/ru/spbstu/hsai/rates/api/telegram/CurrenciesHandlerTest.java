package ru.spbstu.hsai.rates.api.telegram;

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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CurrenciesHandlerTest.TestConfig.class)
public class CurrenciesHandlerTest {

    @Configuration
    @PropertySource("classpath:command.properties")
    static class TestConfig {
        @Bean
        public RatesServiceImpl ratesService() {
            return mock(RatesServiceImpl.class);
        }

        @Bean
        public HistorySDK historySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public CurrenciesHandler currenciesHandler(RatesServiceImpl ratesService, HistorySDK historySDK) {
            return new CurrenciesHandler(ratesService, historySDK);
        }
    }

    @Autowired
    private RatesServiceImpl ratesService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private CurrenciesHandler currenciesHandler;

    @Value("${command.currencies}")
    private String successMessage;

    @Value("${command.currencies.error}")
    private String errorMessage;

    @AfterEach
    void tearDown() {
        reset(ratesService, historySDK);
    }

    @Test
    void handleShouldReturnFormattedCurrencies() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/currencies");
        List<CurrencyDBO> testCurrencies = List.of(
                new CurrencyDBO("USD", "US Dollar", LocalDateTime.now()),
                new CurrencyDBO("EUR", "Euro", LocalDateTime.now()),
                new CurrencyDBO("GBP", "British Pound", LocalDateTime.now())
        );

        when(ratesService.getAllCurrencies()).thenReturn(Flux.fromIterable(testCurrencies));
        when(historySDK.saveHistory(anyLong(), anyString(), any(), anyMap()))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(currenciesHandler.handle(message))
                .expectNextMatches(result ->
                        result.contains("USD (US Dollar)") &&
                                result.contains("EUR (Euro)") &&
                                result.contains("GBP (British Pound)"))
                .verifyComplete();

        verify(ratesService).getAllCurrencies();
        verifyHistorySaved("/currencies");
    }

    @Test
    void handleShouldReturnErrorWhenNoCurrencies() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/currencies");
        when(ratesService.getAllCurrencies()).thenReturn(Flux.empty());

        // Act & Assert
        StepVerifier.create(currenciesHandler.handle(message))
                .expectNext(errorMessage)
                .verifyComplete();

        verify(historySDK, never()).saveHistory(anyLong(), anyString(), any(), anyMap());
    }

    @Test
    void saveHistoryShouldBeCalledWithCorrectParams() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/currencies");
        String result = "USD (US Dollar), EUR (Euro)";
        when(ratesService.getAllCurrencies()).thenReturn(Flux.just(
                new CurrencyDBO("USD", "US Dollar", LocalDateTime.now()),
                new CurrencyDBO("EUR", "Euro", LocalDateTime.now())
        ));

        // Act
        StepVerifier.create(currenciesHandler.handle(message)).expectNextCount(1).verifyComplete();

        // Assert
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(historySDK).saveHistory(
                eq(12345L),
                eq("CURRENCIES"),
                isNull(),
                payloadCaptor.capture()
        );

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("/currencies", payload.get("request"));
        assertTrue(((String) payload.get("result")).contains("USD (US Dollar)"));
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(12345L, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String request) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historySDK).saveHistory(
                eq(12345L),
                eq("CURRENCIES"),
                isNull(),
                captor.capture()
        );

        Map<String, Object> payload = captor.getValue();
        assertEquals(request, payload.get("request"));
        assertNotNull(payload.get("result"));
    }
}
package ru.spbstu.hsai.alert.api.telegram;

import org.bson.types.ObjectId;
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
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.user.RatesService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AlertAddHandlerTest.TestConfig.class)
public class AlertAddHandlerTest {

    @Configuration
    @PropertySource("classpath:command.properties")
    static class TestConfig {
        @Bean
        public AlertService alertService() {
            return mock(AlertService.class);
        }

        @Bean
        public RatesService ratesService() {
            return mock(RatesService.class);
        }

        @Bean
        public HistorySDK historySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public AlertAddHandler alertAddHandler(AlertService alertService,
                                               RatesService ratesService,
                                               HistorySDK historySDK) {
            return new AlertAddHandler(alertService, ratesService, historySDK);
        }
    }

    @Autowired
    private AlertService alertService;

    @Autowired
    private RatesService ratesService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private AlertAddHandler alertAddHandler;

    @Value("${command.alert.success}")
    private String successMessage;

    @Value("${command.alert.error}")
    private String errorMessage;

    @Value("${command.alert.error.format}")
    private String formatErrorMessage;

    @Value("${command.alert.error.pair}")
    private String pairNotFoundMessage;

    private final Long chatId = 12345L;

    @AfterEach
    void tearDown() {
        reset(alertService, ratesService, historySDK);
    }

    @Test
    void handleValidCommandShouldCreateAlert() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_add USD/EUR >1.2");
        when(ratesService.getCurrencyPairId("USD", "EUR"))
                .thenReturn(Mono.just(ObjectId.get()));
        when(alertService.addAlert(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertAddHandler.handle(message))
                .expectNext(successMessage)
                .verifyComplete();

        verify(ratesService).getCurrencyPairId("USD", "EUR");
        verify(alertService).addAlert(chatId, "USD/EUR", ">1.2");
        verifyHistorySaved("USD/EUR", "/alert_add USD/EUR >1.2");
    }

    @Test
    void handleCaseInsensitiveCommandShouldWork() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_add eur/gbp <0.9");
        when(ratesService.getCurrencyPairId("EUR", "GBP"))
                .thenReturn(Mono.just(ObjectId.get()));
        when(alertService.addAlert(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertAddHandler.handle(message))
                .expectNext(successMessage)
                .verifyComplete();

        verify(ratesService).getCurrencyPairId("EUR", "GBP");
        verify(alertService).addAlert(chatId, "EUR/GBP", "<0.9");
    }

    @Test
    void handleInvalidFormatShouldReturnError() {
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        // Arrange
        Message message = createMessage("/alert_add invalid_command");

        // Act & Assert
        StepVerifier.create(alertAddHandler.handle(message))
                .expectNext(formatErrorMessage)
                .verifyComplete();

        verifyNoInteractions(ratesService, alertService);
        verifyNoInteractions(historySDK);
    }

    @Test
    void handleServiceErrorShouldReturnGenericError() {
        // Arrange
        Message message = createMessage("/alert_add USD/EUR ==1.0");
        when(ratesService.getCurrencyPairId("USD", "EUR"))
                .thenReturn(Mono.just(ObjectId.get()));
        when(alertService.addAlert(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        // Act & Assert
        StepVerifier.create(alertAddHandler.handle(message))
                .expectNext(errorMessage)
                .verifyComplete();
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String currencyPair, String request) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        verify(historySDK).saveHistory(
                eq(chatId),
                eq("ALERT_ADD"),
                eq(currencyPair),
                captor.capture()
        );

        Map<String, Object> payload = captor.getValue();
        assertEquals(request, payload.get("request"));
        assertEquals(successMessage, payload.get("result"));
    }
}
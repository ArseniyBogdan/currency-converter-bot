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
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.alert.service.AlertService;
import ru.spbstu.hsai.history.HistorySDK;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AlertListHandlerTest.TestConfig.class)
public class AlertListHandlerTest {

    @Configuration
    @PropertySource("classpath:command.properties")
    static class TestConfig {
        @Bean
        public AlertService alertService() {
            return mock(AlertService.class);
        }

        @Bean
        public HistorySDK historySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public AlertListHandler alertListHandler(AlertService alertService, HistorySDK historySDK) {
            return new AlertListHandler(alertService, historySDK);
        }
    }

    @Autowired
    private AlertService alertService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private AlertListHandler alertListHandler;

    @Value("${command.alert.list.success.empty}")
    private String emptyListMessage;

    @Value("${command.alert.list.success.filled}")
    private String filledListMessage;

    @Value("${command.alert.list.error.chat}")
    private String chatNotFoundMessage;

    private final Long chatId = 12345L;

    @AfterEach
    void tearDown() {
        reset(alertService, historySDK);
    }

    @Test
    void handleEmptyAlertListShouldReturnEmptyMessage() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_list");
        when(alertService.getAllAlertsByChatId(eq(chatId))).thenReturn(Mono.just(List.of()));

        // Act & Assert
        StepVerifier.create(alertListHandler.handle(message))
                .expectNext(emptyListMessage)
                .verifyComplete();

        verifyHistorySaved("/alert_list", emptyListMessage);
    }

    @Test
    void handleNonEmptyAlertListShouldReturnFormattedList() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_list");
        List<AlertDBO> alerts = List.of(
                createAlert("USD", "EUR", ">1.2"),
                createAlert("GBP", "USD", "<0.8")
        );

        when(alertService.getAllAlertsByChatId(chatId)).thenReturn(Mono.just(alerts));

        // Act & Assert
        StepVerifier.create(alertListHandler.handle(message))
                .expectNextMatches(result ->
                        result.startsWith(filledListMessage) &&
                                result.contains("1. USD/EUR >1.2") &&
                                result.contains("2. GBP/USD <0.8")
                )
                .verifyComplete();

        verifyHistorySaved("/alert_list", filledListMessage);
    }

    @Test
    void handleChatNotFoundShouldReturnError() {
        // Arrange
        Message message = createMessage("/alert_list");
        when(alertService.getAllAlertsByChatId(chatId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertListHandler.handle(message))
                .expectNext(chatNotFoundMessage)
                .verifyComplete();

        verify(historySDK, never()).saveHistory(anyLong(), anyString(), any(), anyMap());
    }

    private AlertDBO createAlert(String base, String target, String expr) {
        AlertDBO alert = new AlertDBO();
        alert.setBaseCurrency(base);
        alert.setTargetCurrency(target);
        alert.setExpr(expr);
        alert.setId(ObjectId.get());
        return alert;
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String request, String result) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        verify(historySDK).saveHistory(
                eq(chatId),
                eq("ALERT_LIST"),
                isNull(),
                captor.capture()
        );

        Map<String, Object> payload = captor.getValue();
        assertEquals(request, payload.get("request"));
        assertTrue(((String)payload.get("result")).contains(result));
    }
}
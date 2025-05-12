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
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AlertRemoveHandlerTest.TestConfig.class)
public class AlertRemoveHandlerTest {

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
        public AlertRemoveHandler alertRemoveHandler(AlertService alertService, HistorySDK historySDK) {
            return new AlertRemoveHandler(alertService, historySDK);
        }
    }

    @Value("${command.alert.remove.error.id}")
    private String errorAlertNotFound;

    @Autowired
    private AlertService alertService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private AlertRemoveHandler alertRemoveHandler;

    private final Long chatId = 12345L;
    private final String alertId = ObjectId.get().toString();

    @AfterEach
    void tearDown() {
        reset(alertService, historySDK);
    }

    @Test
    void handleValidCommandShouldDeleteAlert() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_remove " + alertId);
        when(alertService.deleteAlert(alertId, chatId)).thenReturn(Mono.just("Alert deleted"));

        // Act & Assert
        StepVerifier.create(alertRemoveHandler.handle(message))
                .expectNext("Alert deleted")
                .verifyComplete();

        verify(alertService).deleteAlert(alertId, chatId);
        verifyHistorySaved("/alert_remove " + alertId, "Alert deleted");
    }

    @Test
    void handleCommandWithoutIdShouldReturnError() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_remove");

        // Act & Assert
        StepVerifier.create(alertRemoveHandler.handle(message))
                .expectNext(errorAlertNotFound)
                .verifyComplete();

        verifyNoInteractions(alertService);
        verifyNoInteractions(historySDK);
    }

    @Test
    void handleNonExistingAlertShouldReturnError() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/alert_remove invalid_id");
        when(alertService.deleteAlert("invalid_id", chatId))
                .thenReturn(Mono.error(new CCBException("Alert not found")));

        // Act & Assert
        StepVerifier.create(alertRemoveHandler.handle(message))
                .expectNext("Alert not found")
                .verifyComplete();

        verify(alertService).deleteAlert("invalid_id", chatId);
    }

    @Test
    void handleServiceErrorShouldPropagateMessage() {
        // Arrange
        Message message = createMessage("/alert_remove " + alertId);
        when(alertService.deleteAlert(alertId, chatId))
                .thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(alertRemoveHandler.handle(message))
                .expectNext("Database error")
                .verifyComplete();
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
                eq("ALERT_REMOVE"),
                isNull(),
                captor.capture()
        );

        Map<String, Object> payload = captor.getValue();
        assertEquals(request, payload.get("request"));
        assertEquals(result, payload.get("result"));
    }
}

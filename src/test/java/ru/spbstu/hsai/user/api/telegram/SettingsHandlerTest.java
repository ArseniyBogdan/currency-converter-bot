package ru.spbstu.hsai.user.api.telegram;

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
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.user.UserSettings;
import ru.spbstu.hsai.user.service.UserServiceImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SettingsHandlerTest.TestConfig.class)
public class SettingsHandlerTest {

    @Configuration
    @PropertySource("classpath:command.properties")
    static class TestConfig {
        @Bean
        public UserServiceImpl userService() {
            return mock(UserServiceImpl.class);
        }

        @Bean
        public HistorySDK historySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public SettingsHandler settingsHandler(UserServiceImpl userService, HistorySDK historySDK) {
            return new SettingsHandler(userService, historySDK);
        }
    }

    @Autowired
    private SettingsHandler settingsHandler;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private HistorySDK historySDK;

    @Value("${command.settings}")
    private String settingsFormat;

    @AfterEach
    void tearDown() {
        reset(userService, historySDK);
    }

    @Test
    void handleShouldReturnFormattedSettings() {
        // Arrange
        Message message = createMessage("/settings");
        UserSettings mockSettings = new UserSettings("USD", "USD/EUR");
        when(userService.getUserSettings(anyLong())).thenReturn(Mono.just(mockSettings));
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());

        String expectedResponse = String.format(settingsFormat,
                mockSettings.getHomeCurrency(),
                mockSettings.getDefaultPair());

        // Act & Assert
        StepVerifier.create(settingsHandler.handle(message))
                .expectNext(expectedResponse)
                .verifyComplete();

        verify(userService).getUserSettings(12345L);
        verifyHistorySaved("SETTINGS", "/settings", expectedResponse);
    }

    @Test
    void handleShouldSaveHistoryAfterResponse() {
        // Arrange
        Message message = createMessage("/settings");
        UserSettings mockSettings = new UserSettings("EUR", "EUR/GBP");
        when(userService.getUserSettings(anyLong())).thenReturn(Mono.just(mockSettings));
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(settingsHandler.handle(message))
                .expectNextCount(1)
                .verifyComplete();

        // Assert
        verify(historySDK).saveHistory(
                eq(12345L),
                eq("SETTINGS"),
                isNull(),
                anyMap());
    }

    @Test
    void handleShouldPropagateServiceErrors() {
        // Arrange
        Message message = createMessage("/settings");
        when(userService.getUserSettings(anyLong()))
                .thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(settingsHandler.handle(message))
                .expectError(RuntimeException.class)
                .verify();
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(12345L, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String operation, String request, String result) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(historySDK).saveHistory(
                eq(12345L),
                eq(operation),
                isNull(),
                captor.capture());

        Map<String, Object> params = captor.getValue();
        assertEquals(request, params.get("request"));
        assertEquals(result, params.get("result"));
    }
}
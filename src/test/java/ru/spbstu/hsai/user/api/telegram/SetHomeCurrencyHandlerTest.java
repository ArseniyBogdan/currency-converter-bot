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
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.user.service.UserServiceImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {SetHomeCurrencyHandlerTest.Config.class})
public class SetHomeCurrencyHandlerTest {

    @Configuration
    @PropertySource(value = "classpath:command.properties", encoding = "UTF-8")
    static class Config {
        @Bean
        public UserServiceImpl provideUserService(){
            return mock(UserServiceImpl.class);
        }

        @Bean
        public HistorySDK provideHistorySDK(){
            return mock(HistorySDK.class);
        }

        @Bean
        public SetHomeCurrencyHandler clearHistoryHandler(UserServiceImpl historyService, HistorySDK historySDK) {
            return new SetHomeCurrencyHandler(historyService, historySDK);
        }
    }

    @Value("${command.sethome.success}")
    private String commandSetHomeCurrencyReply;

    @Value("${command.sethome.error}")
    private String commandFormat;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private SetHomeCurrencyHandler handler;

    @AfterEach
    void tearDown() {
        reset(userService);
        reset(historySDK);
    }

    @Test
    void handle_ValidCommand_ShouldSetCurrencyAndReturnSuccess() {
        // Arrange
        Message message = createMessage("/sethome USD");
        when(userService.setHomeCurrency(anyLong(), anyString())).thenReturn(Mono.empty());
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handler.handle(message))
                .expectNext(commandSetHomeCurrencyReply)
                .verifyComplete();

        verify(userService).setHomeCurrency(12345L, "USD");
        verifyHistorySaved("SET_HOME", "/sethome USD", commandSetHomeCurrencyReply);
    }

    @Test
    void handle_InvalidFormat_ShouldThrowException() {
        // Arrange
        Message message = createMessage("/sethome invalid");

        // Act & Assert
        StepVerifier.create(handler.handle(message))
                .expectError(CCBException.class)
                .verify();

        verifyNoInteractions(userService);
    }

    @Test
    void handle_UpperCaseConversion_ShouldWork() {
        // Arrange
        Message message = createMessage("/sethome EUR");
        when(userService.setHomeCurrency(anyLong(), anyString())).thenReturn(Mono.empty());
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handler.handle(message))
                .expectNext(commandSetHomeCurrencyReply)
                .verifyComplete();

        verify(userService).setHomeCurrency(12345L, "EUR");
    }

    @Test
    void handle_ServiceError_ShouldPropagateError() {
        // Arrange
        Message message = createMessage("/sethome USD");
        when(userService.setHomeCurrency(anyLong(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        // Act & Assert
        StepVerifier.create(handler.handle(message))
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
                captor.capture()
        );

        Map<String, Object> params = captor.getValue();
        assertEquals(request, params.get("request"));
        assertEquals(result, params.get("result"));
    }
}
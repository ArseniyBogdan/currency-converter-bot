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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SetPairHandlerTest.TestConfig.class)
public class SetPairHandlerTest {

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
        public SetPairHandler setPairHandler(UserServiceImpl userService, HistorySDK historySDK) {
            return new SetPairHandler(historySDK, userService);
        }
    }

    @Autowired
    private SetPairHandler setPairHandler;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private HistorySDK historySDK;

    @Value("${command.setpair.success}")
    private String successMessage;

    @Value("${command.setpair.error.format}")
    private String formatErrorMessage;

    @Value("${command.setpair.error.equals}")
    private String equalsErrorMessage;

    @AfterEach
    void tearDown() {
        reset(userService, historySDK);
    }

    @Test
    void handleValidCommandShouldReturnSuccess() {
        Message message = createMessage("/setpair USD/EUR");
        when(userService.setPair(anyLong(), anyString(), anyString())).thenReturn(Mono.empty());
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());

        StepVerifier.create(setPairHandler.handle(message))
                .expectNext(successMessage)
                .verifyComplete();

        verify(userService).setPair(12345L, "USD", "EUR");
        verifyHistorySaved("SET_PAIR", "/setpair USD/EUR", successMessage);
    }

    @Test
    void handleInvalidFormatShouldThrowError() {
        Message message = createMessage("/setpair USD-EUR");

        StepVerifier.create(setPairHandler.handle(message))
                .expectErrorMatches(ex -> ex instanceof CCBException &&
                        ex.getMessage().equals(formatErrorMessage))
                .verify();
    }

    @Test
    void handleSameCurrenciesShouldThrowError() {
        Message message = createMessage("/setpair USD/USD");

        StepVerifier.create(setPairHandler.handle(message))
                .expectErrorMatches(ex -> ex instanceof CCBException &&
                        ex.getMessage().equals(equalsErrorMessage))
                .verify();
    }

    @Test
    void handleServiceErrorShouldPropagate() {
        Message message = createMessage("/setpair USD/EUR");
        when(userService.setPair(anyLong(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(setPairHandler.handle(message))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldSaveHistoryAfterSuccess() {
        Message message = createMessage("/setpair GBP/JPY");
        when(userService.setPair(anyLong(), anyString(), anyString())).thenReturn(Mono.empty());
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());

        StepVerifier.create(setPairHandler.handle(message))
                .expectNextCount(1)
                .verifyComplete();

        verify(historySDK).saveHistory(
                eq(12345L),
                eq("SET_PAIR"),
                isNull(),
                anyMap()
        );
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
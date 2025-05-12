package ru.spbstu.hsai.rates.api.telegram;

import org.bson.types.ObjectId;
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
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;
import ru.spbstu.hsai.user.UserDTO;
import ru.spbstu.hsai.user.UserServiceSDK;
import ru.spbstu.hsai.user.UserSettings;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RateHandlerTest.TestConfig.class)
public class RateHandlerTest {

    @Configuration
    @PropertySource("classpath:command.properties")
    static class TestConfig {
        @Bean
        public RatesServiceImpl ratesService() {
            return mock(RatesServiceImpl.class);
        }

        @Bean
        public UserServiceSDK userService() {
            return mock(UserServiceSDK.class);
        }

        @Bean
        public HistorySDK historySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public RateHandler rateHandler(RatesServiceImpl ratesService,
                                       UserServiceSDK userService,
                                       HistorySDK historySDK) {
            return new RateHandler(ratesService, userService, historySDK);
        }
    }

    @Autowired
    private RatesServiceImpl ratesService;

    @Autowired
    private UserServiceSDK userService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private RateHandler rateHandler;

    @Value("${command.rate.success}")
    private String successTemplate;

    @Value("${command.rate.error.format}")
    private String errorFormat;

    @Value("${command.rate.error.pair}")
    private String errorPairNotSpecified;

    @Value("${command.rate.error.settings}")
    private String errorSettingsNotFound;

    @Value("${command.rate.error.currency}")
    private String errorCurrencyNotSpecified;

    private final Long chatId = 12345L;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Test
    void handleCurrencyPairRequestSuccess() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/rate USD/EUR");
        LocalDateTime now = LocalDateTime.now();

        when(ratesService.getExchangeRate("USD", "EUR"))
                .thenReturn(Mono.just(
                        new CurrencyPairDBO(ObjectId.get(), "USD",
                                "EUR", BigDecimal.valueOf(0.85), LocalDateTime.now())));

        // Act & Assert
        StepVerifier.create(rateHandler.handle(message))
                .expectNextMatches(result ->
                        result.contains("USD/EUR") &&
                                result.contains("0,8500") &&
                                result.contains(now.format(formatter)))
                                        .verifyComplete();

        verifyHistorySaved("USD/EUR", "/rate USD/EUR");
    }

    @Test
    void handleSingleCurrencyRequestSuccess() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/rate EUR");
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        LocalDateTime now = LocalDateTime.now();

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(12345L, LocalDateTime.now(), settings)));
        when(ratesService.getExchangeRate("USD", "EUR"))
                .thenReturn(Mono.just(
                        new CurrencyPairDBO(ObjectId.get(), "USD",
                                "EUR", BigDecimal.valueOf(0.85), LocalDateTime.now())));

        // Act & Assert
        StepVerifier.create(rateHandler.handle(message))
                .expectNextMatches(result -> result.contains("USD/EUR"))
                .verifyComplete();
    }

    @Test
    void handleDefaultPairRequestSuccess() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/rate");
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        LocalDateTime now = LocalDateTime.now();

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(12345L, LocalDateTime.now(), settings)));
        when(ratesService.getExchangeRate("USD", "EUR"))
                .thenReturn(Mono.just(
                        new CurrencyPairDBO(ObjectId.get(), "USD",
                                "EUR", BigDecimal.valueOf(0.85), LocalDateTime.now())));

        // Act & Assert
        StepVerifier.create(rateHandler.handle(message))
                .expectNextMatches(result -> result.contains("USD/EUR"))
                .verifyComplete();
    }

    @Test
    void handleInvalidFormatError() {
        Message message = createMessage("/rate invalid");

        StepVerifier.create(rateHandler.handle(message))
                .expectNext(errorFormat)
                .verifyComplete();
    }

    @Test
    void handleMissingDefaultPairError() {
        Message message = createMessage("/rate");
        UserSettings settings = new UserSettings("USD", null);

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(12345L, LocalDateTime.now(), settings)));

        StepVerifier.create(rateHandler.handle(message))
                .expectNext(errorPairNotSpecified)
                .verifyComplete();
    }

    @Test
    void handleMissingHomeCurrencyError() {
        Message message = createMessage("/rate EUR");
        UserSettings settings = new UserSettings(null, "USD/EUR");

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(12345L, LocalDateTime.now(), settings)));

        StepVerifier.create(rateHandler.handle(message))
                .expectNext(errorCurrencyNotSpecified)
                .verifyComplete();
    }

    @Test
    void handleRateNotFoundError() {
        Message message = createMessage("/rate USD/JPY");

        when(ratesService.getExchangeRate("USD", "JPY")).thenReturn(Mono.empty());

        StepVerifier.create(rateHandler.handle(message))
                .expectNextMatches(msg -> msg.contains("USD/JPY"))
                .verifyComplete();
    }

    @Test
    void handleUserSettingsNotFoundError() {
        Message message = createMessage("/rate");

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.empty());

        StepVerifier.create(rateHandler.handle(message))
                .expectNext(errorSettingsNotFound)
                .verifyComplete();
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String currencyCode, String request) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        verify(historySDK).saveHistory(
                eq(chatId),
                eq("RATE"),
                eq(currencyCode),
                captor.capture()
        );

        Map<String, Object> payload = captor.getValue();
        assertEquals(request, payload.get("request"));
        assertTrue(((String) payload.get("result")).contains(currencyCode));
    }

    // Вспомогательный класс для мокирования RateData
    private static class RateData {
        private final BigDecimal currentRate;
        private final LocalDateTime updated;

        public RateData(BigDecimal currentRate, LocalDateTime updated) {
            this.currentRate = currentRate;
            this.updated = updated;
        }

        public BigDecimal getCurrentRate() { return currentRate; }
        public LocalDateTime getUpdated() { return updated; }
    }
}
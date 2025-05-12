package ru.spbstu.hsai.rates.api.telegram;


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
@ContextConfiguration(classes = ConvertHandlerTest.TestConfig.class)
public class ConvertHandlerTest {

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
        public ConvertHandler convertHandler(RatesServiceImpl ratesService,
                                             UserServiceSDK userService,
                                             HistorySDK historySDK) {
            return new ConvertHandler(ratesService, userService, historySDK);
        }
    }

    @Autowired
    private RatesServiceImpl ratesService;

    @Autowired
    private UserServiceSDK userService;

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private ConvertHandler convertHandler;

    @Value("${command.convert.success}")
    private String successTemplate;

    @Value("${command.convert.error}")
    private String errorMessage;

    @Value("${command.convert.error.format}")
    private String errorFormat;

    @Value("${command.convert.error.settings}")
    private String errorSettingsNotFound;

    @Value("${command.convert.error.currency}")
    private String errorCurrencyNotSpecified;

    @Value("${command.convert.error.pair}")
    private String errorPairNotSpecified;

    @Value("${command.convert.error.rate}")
    private String errorRateNotFound;

    private final Long chatId = 12345L;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @AfterEach
    void tearDown() {
        reset(ratesService, userService, historySDK);
    }

    @Test
    void handleValidFullCommandShouldReturnConversion() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/convert 100 USD EUR");
        UserSettings settings = new UserSettings("EUR", "USD/EUR");
        LocalDateTime now = LocalDateTime.now();

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(chatId, LocalDateTime.now(), settings)));
        when(ratesService.getExchangeRate("USD", "EUR"))
                .thenReturn(Mono.just(new CurrencyPairDBO(ObjectId.get(), "USD", "EUR", BigDecimal.valueOf(0.85), LocalDateTime.now())));

        // Act & Assert
        StepVerifier.create(convertHandler.handle(message))
                .expectNextMatches(result ->
                        result.contains("100,00 USD") &&
                                result.contains("85,00 EUR") &&
                                result.contains(now.format(formatter)))
                .verifyComplete();

        verifyHistorySaved("USD/EUR", 100.0);
    }

    @Test
    void handleCommandWithDefaultPairShouldUseSettings() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/convert 200");
        UserSettings settings = new UserSettings("EUR", "USD/EUR");
        LocalDateTime now = LocalDateTime.now();

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(chatId, LocalDateTime.now(), settings)));
        when(ratesService.getExchangeRate("USD", "EUR"))
                .thenReturn(Mono.just(new CurrencyPairDBO(ObjectId.get(), "USD", "EUR", BigDecimal.valueOf(0.85), LocalDateTime.now())));

        // Act & Assert
        StepVerifier.create(convertHandler.handle(message))
                .expectNextMatches(result -> result.contains("170,00 EUR"))
                .verifyComplete();
    }

    @Test
    void handleCommandWithHomeCurrencyShouldUseHomeCurrency() {
        // Arrange
        when(historySDK.saveHistory(anyLong(), anyString(), anyString(), anyMap())).thenReturn(Mono.empty());
        Message message = createMessage("/convert 300 GBP");
        UserSettings settings = new UserSettings("EUR", "USD/EUR");
        LocalDateTime now = LocalDateTime.now();

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(chatId, LocalDateTime.now(), settings)));
        when(ratesService.getExchangeRate("GBP", "EUR"))
                .thenReturn(Mono.just(new CurrencyPairDBO(ObjectId.get(), "GBP", "EUR", BigDecimal.valueOf(1.15), LocalDateTime.now())));

        // Act & Assert
        StepVerifier.create(convertHandler.handle(message))
                .expectNextMatches(result -> result.contains("345,00 EUR"))
                .verifyComplete();
    }

    @Test
    void handleInvalidFormatShouldReturnError() {
        Message message = createMessage("/convert invalid");

        StepVerifier.create(convertHandler.handle(message))
                .expectNext(errorFormat)
                .verifyComplete();
    }

    @Test
    void handleMissingSettingsShouldReturnError() {
        Message message = createMessage("/convert 100");

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.empty());

        StepVerifier.create(convertHandler.handle(message))
                .expectNext(errorSettingsNotFound)
                .verifyComplete();
    }

    @Test
    void handleMissingRateShouldReturnError() {
        Message message = createMessage("/convert 100 USD JPY");
        UserSettings settings = new UserSettings("EUR", "USD/EUR");

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(new UserDTO(chatId, LocalDateTime.now(), settings)));
        when(ratesService.getExchangeRate("USD", "JPY")).thenReturn(Mono.empty());

        StepVerifier.create(convertHandler.handle(message))
                .expectNextMatches(msg -> msg.contains("JPY"))
                .verifyComplete();
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setText(text);
        return message;
    }

    private void verifyHistorySaved(String currencyPair, double amount) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        verify(historySDK).saveHistory(
                eq(chatId),
                eq("CONVERT"),
                eq(currencyPair),
                captor.capture()
        );

        Map<String, Object> payload = captor.getValue();
        assertEquals(amount, payload.get("amount"));
        assertNotNull(payload.get("result"));
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
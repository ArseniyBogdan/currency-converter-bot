package ru.spbstu.hsai.history.api.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import ru.spbstu.hsai.DocumentationGenerator;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.service.HistoryService;
import ru.spbstu.hsai.telegram.CurrencyConverterBot;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ClearHistoryHandlerTest.Config.class})
public class ClearHistoryHandlerTest {

    @Configuration
    @PropertySource(value = "classpath:command.properties", encoding = "UTF-8")
    static class Config {
        @Bean
        public HistoryService historyService() {
            return mock(HistoryService.class);
        }

        @Bean
        public CurrencyConverterBot bot() {
            return mock(CurrencyConverterBot.class);
        }

        @Bean
        public ClearHistoryHandler clearHistoryHandler(HistoryService historyService, CurrencyConverterBot bot) {
            return new ClearHistoryHandler(historyService, bot);
        }
    }

    @Value("${command.clear_history.success}")
    private String successMessage;

    @Value("${command.clear_history.error}")
    private String errorMessage;

    @Autowired
    private ClearHistoryHandler handler;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private CurrencyConverterBot bot;

    @Test
    public void testClearHistorySuccess() {
        // Arrange
        Message message = new Message();
        message.setChat(new Chat(12345L, "private"));

        when(historyService.deleteByChatId(anyLong())).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(handler.handle(message))
                .expectNext(successMessage)
                .verifyComplete();

        DocumentationGenerator.generateCommandDoc(
                "/clear_history",
                "Очищает историю операций пользователя",
                "/clear_history"
        );
    }

    @Test
    public void testClearHistoryError() {
        // Arrange
        Message message = new Message();
        message.setChat(new Chat(12345L, "private"));

        when(historyService.deleteByChatId(anyLong()))
                .thenReturn(Mono.error(new CCBException("errorMessage")));

        // Act & Assert
        StepVerifier.create(handler.handle(message))
                .expectNext(errorMessage)
                .verifyComplete();

        verify(bot).sendMessage(12345L, errorMessage);
    }
}
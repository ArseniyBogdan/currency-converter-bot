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
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.history.entities.HistoryDBO;
import ru.spbstu.hsai.history.service.CSVExporter;
import ru.spbstu.hsai.history.service.HistoryService;
import ru.spbstu.hsai.telegram.CurrencyConverterBot;

import java.io.File;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ExportHandlerTest.Config.class})
public class ExportHandlerTest {

    @Configuration
    @PropertySource(value = "classpath:command.properties", encoding = "UTF-8")
    static class Config {
        @Bean
        public HistorySDK provideHistorySDK() {
            return mock(HistorySDK.class);
        }

        @Bean
        public HistoryService provideHistoryService() {
            return mock(HistoryService.class);
        }

        @Bean
        public CSVExporter provideCSVExporter() {
            return mock(CSVExporter.class);
        }

        @Bean
        public CurrencyConverterBot provideCurrencyConverterBot() {
            return mock(CurrencyConverterBot.class);
        }

        @Bean
        public ExportHandler provideExportHandler(
                HistorySDK historySDK,
               HistoryService historyService,
               CSVExporter csvExporter,
               CurrencyConverterBot bot
        ) {
            return new ExportHandler(historySDK, historyService, csvExporter, bot);
        }
    }

    @Autowired
    private HistorySDK historySDK;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private CSVExporter csvExporter;

    @Autowired
    private CurrencyConverterBot bot;

    @Autowired
    private ExportHandler exportHandler;

    @Value("${command.export.success}")
    private String successMessage;

    @Value("${command.export.error}")
    private String errorMessage;

    @Value("${command.export.error.parameters}")
    private String errorCountParameters;

    @Value("${command.export.error.date.format}")
    private String errorDateFormat;

    @Test
    void handleSuccessfulExportWithAllParameters() throws TelegramApiException {
        // Arrange
        Message message = createMessage("/export 2023-01-01 2023-12-31 USD");
        File testFile = new File("test.csv");

        when(historyService.getHistory(anyLong(), any(), any(), anyString()))
                .thenReturn(Flux.just(new HistoryDBO()));
        when(historySDK.saveHistory(anyLong(), anyString(), isNull(), anyMap())).thenReturn(Mono.empty());
        when(csvExporter.exportToCSV(anyList())).thenReturn(Mono.just(testFile));

        // Act & Assert
        StepVerifier.create(exportHandler.handle(message))
                .expectNext(successMessage)
                .verifyComplete();

        verify(historyService).getHistory(
                12345L,
                LocalDate.parse("2023-01-01").atStartOfDay(),
                LocalDate.parse("2023-12-31").atStartOfDay(),
                "USD"
        );
        verify(csvExporter).exportToCSV(anyList());
        verify(bot).execute(any(SendDocument.class));
    }

    @Test
    void handle_InvalidDateFormat_ShouldReturnErrorMessage() {
        // Arrange
        Message message = createMessage("/export invalid-date");

        // Act & Assert
        StepVerifier.create(exportHandler.handle(message))
                .expectNext(errorDateFormat + "invalid-date")
                .verifyComplete();

        verify(bot).sendMessage(eq(12345L), contains(errorDateFormat));
    }

    @Test
    void handle_TooManyParameters_ShouldThrowException() {
        // Arrange
        Message message = createMessage("/export 2023-01-01 2023-12-31 USD extra-param");

        // Act & Assert
        StepVerifier.create(exportHandler.handle(message))
                .expectNext(errorCountParameters)
                .verifyComplete();
    }

    @Test
    void handle_ExportError_ShouldSendErrorMessage() {
        // Arrange
        Message message = createMessage("/export");
        when(historyService.getHistory(anyLong(), isNull(), isNull(), isNull()))
                .thenReturn(Flux.error(new RuntimeException("DB error")));

        // Act & Assert
        StepVerifier.create(exportHandler.handle(message))
                .expectNext(errorMessage)
                .verifyComplete();

        verify(bot).sendMessage(eq(12345L), eq(errorMessage));
    }

    private Message createMessage(String text) {
        Message message = new Message();
        message.setChat(new Chat(12345L, "private"));
        message.setText(text);
        return message;
    }
}
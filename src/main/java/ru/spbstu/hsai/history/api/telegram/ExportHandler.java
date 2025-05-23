package ru.spbstu.hsai.history.api.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.history.service.CSVExporter;
import ru.spbstu.hsai.history.service.HistoryService;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.telegram.CurrencyConverterBot;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Обработчик команды /clear_history для экспорта истории
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportHandler implements CommandHandler {
    private final HistorySDK historySDK;
    private final HistoryService historyService;
    private final CSVExporter csvExporter;
    private final CurrencyConverterBot bot;

    @Value("${command.export.success}")
    private String successMessage;

    @Value("${command.export.error}")
    private String errorMessage;

    @Value("${command.export.error.parameters}")
    private String errorCountParameters;

    @Value("${command.export.error.date.format}")
    private String errorDateFormat;

    /**
     * Обрабатывает команду /export, производит экспорт истории в .csv файл
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом формирования файла или ошибку (после отдельно отправляется файл)
     */
    @Override
    @BotCommand("/export")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> processExportCommand(
                        message.getChatId(),
                        commandText.split("\\s+")
                )).map(result -> {
                    saveHistory(message.getChatId(), message.getText(), result);
                    return result;
                })
                .onErrorResume(e -> handleError(message.getChatId(), e));
    }

    private Mono<String> processExportCommand(Long chatId, String[] parts) {
        return parseParameters(parts)
                .flatMapMany(params -> historyService.getHistory(
                        chatId,
                        params.getStartDate(),
                        params.getEndDate(),
                        params.getCurrency()
                ))
                .collectList()
                .flatMap(csvExporter::exportToCSV)
                .flatMap(file -> sendFileToUser(chatId, file))
                .thenReturn(successMessage);
    }

    private Mono<ExportParams> parseParameters(String[] parts) {
        return Mono.fromCallable(() -> {
            LocalDateTime startDate = null;
            LocalDateTime endDate = null;
            String currency = null;

            List<String> params = Arrays.stream(parts)
                    .skip(1) // Пропускаем саму команду /export
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toList());

            // Определяем валюту (последний параметр, если он 3 символа)
            if (!params.isEmpty()) {
                String lastParam = params.getLast();
                if (lastParam.matches("([A-Z]{3}/[A-Z]{3}|[A-Z]{3})")) {
                    currency = params.removeLast().toUpperCase();
                }
            }

            // Парсим даты
            switch (params.size()) {
                case 2:
                    startDate = parseDate(params.get(0));
                    endDate = parseDate(params.get(1));
                    break;
                case 1:
                    startDate = parseDate(params.get(0));
                    endDate = LocalDateTime.now();
                    break;
                case 0:
                    // Без дат - вся история
                    break;
                default:
                    throw new CCBException(errorCountParameters);
            }

            return new ExportParams(startDate, endDate, currency);
        });
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr)
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new CCBException(errorDateFormat + dateStr);
        }
    }

    private Mono<String> handleError(Long chatId, Throwable e) {
        log.error("Export error", e);
        String message = e instanceof CCBException ?
                e.getMessage() : errorMessage;

        bot.sendMessage(chatId, message);
        return Mono.just(message);
    }

    private Mono<File> sendFileToUser(Long chatId, File file) {
        return Mono.fromCallable(() -> {
            SendDocument sendDocument = new SendDocument();
            sendDocument.setChatId(chatId);
            sendDocument.setDocument(new InputFile(file));
            sendDocument.setCaption("История операций");

            try {
                bot.execute(sendDocument);
            } finally {
                file.delete();
            }
            return file;
        });
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String request, String result) {
        historySDK.saveHistory(
                chatId,
                "EXPORT",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }

    @Data
    @AllArgsConstructor
    private static class ExportParams {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String currency;
    }
}

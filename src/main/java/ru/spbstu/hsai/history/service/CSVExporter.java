package ru.spbstu.hsai.history.service;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.entities.HistoryDBO;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CSVExporter {
    private static final String[] HEADERS = {
            "Дата (UTC)", "Тип операции", "Валюта/Пара", "Детали"
    };

    public Mono<File> exportToCSV(List<HistoryDBO> history) {
        return Mono.fromCallable(() -> {
            File file = File.createTempFile("history_", ".csv");

            // Указываем кодировку UTF-8 с BOM
            try (OutputStreamWriter osw = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {

                // Добавляем BOM для совместимости с Excel
                osw.write('\ufeff');

                try (CSVWriter writer = new CSVWriter(osw,
                        ';',
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)) {

                    writer.writeNext(HEADERS);

                    for (HistoryDBO entry : history) {
                        writer.writeNext(new String[]{
                                entry.getCreated().format(DateTimeFormatter.ISO_DATE_TIME),
                                entry.getCommandType(),
                                entry.getCurrencyCode(),
                                formatPayload(entry.getPayload())
                        });
                    }
                }
            }
            return file;
        }).onErrorResume(e -> {
            log.error("CSV export failed", e);
            return Mono.error(e);
        });
    }

    private String formatPayload(Map<String, Object> payload) {
        return payload.entrySet().stream()
                .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
    }
}
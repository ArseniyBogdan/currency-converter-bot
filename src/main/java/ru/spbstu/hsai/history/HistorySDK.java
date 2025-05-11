package ru.spbstu.hsai.history;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface HistorySDK {
    Mono<Void> saveHistory(Long chatId, String commandType,
                           String currencyCode, Map<String, Object> payload);
}

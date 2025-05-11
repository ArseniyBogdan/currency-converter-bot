package ru.spbstu.hsai.history.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.dao.HistoryDAO;
import ru.spbstu.hsai.history.entities.HistoryDBO;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final HistoryDAO historyRepo;

    public Mono<Void> saveHistory(Long chatId, String commandType,
                                  String currencyCode, Map<String, Object> payload, LocalDateTime created) {
        return historyRepo.save(new HistoryDBO(
                null, chatId, commandType,
                currencyCode, payload, created
        )).then();
    }

    public Flux<HistoryDBO> getHistory(Long chatId, LocalDateTime start,
                                       LocalDateTime end, String currencyOrPair) {
        Flux<HistoryDBO> query = historyRepo.findByChatId(chatId);

        if (start != null) {
            query = query.filter(entry ->
                    entry.getCreated().isAfter(start) ||
                            entry.getCreated().isEqual(start)
            );
        }

        if (end != null) {
            query = query.filter(entry ->
                    entry.getCreated().isBefore(end) ||
                            entry.getCreated().isEqual(end)
            );
        }

        if (currencyOrPair != null) {
            if (currencyOrPair.contains("/")) {
                // Фильтрация по валютной паре
                query = query.filter(entry -> {
                        if (entry.getCurrencyCode() == null) {
                            return false;
                        }
                        return currencyOrPair.equalsIgnoreCase(entry.getCurrencyCode());
                    }
                );
            } else {
                // Фильтрация по отдельной валюте
                query = query.filter(entry ->
                    {
                        if (entry.getCurrencyCode() == null) {
                            return false;
                        }
                        return entry.getCurrencyCode().contains(currencyOrPair);
                    }
                );
            }
        }

        return query;
    }

    public Flux<HistoryDBO> getFullHistory(Long chatId) {
        return historyRepo.findByChatId(chatId);
    }

    public Mono<Void> deleteByChatId(Long chatId) {
        return historyRepo.deleteByChatId(chatId);
    }

}

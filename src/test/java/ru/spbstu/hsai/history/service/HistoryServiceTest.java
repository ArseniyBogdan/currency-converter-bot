package ru.spbstu.hsai.history.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.history.dao.HistoryDAO;
import ru.spbstu.hsai.history.entities.HistoryDBO;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private HistoryDAO historyRepo;

    @InjectMocks
    private HistoryService historyService;

    private final LocalDateTime now = LocalDateTime.now();
    private final HistoryDBO testRecord = new HistoryDBO(
            ObjectId.get(), 12345L, "TEST",
            "USD/EUR", Collections.emptyMap(), now
    );

    @Test
    void saveHistory_ShouldCallRepoWithCorrectParams() {
        when(historyRepo.save(any())).thenReturn(Mono.just(testRecord));

        StepVerifier.create(historyService.saveHistory(
                        12345L, "COMMAND",
                        "USD", Map.of("key", "value"), now
                ))
                .verifyComplete();

        verify(historyRepo).save(argThat(history ->
                history.getChatId().equals(12345L) &&
                        history.getCommandType().equals("COMMAND") &&
                        history.getCurrencyCode().equals("USD") &&
                        history.getPayload().containsKey("key") &&
                        history.getCreated().equals(now)
        ));
    }

    @Test
    void getHistory_WithAllFilters_ShouldApplyCorrectFiltering() {
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);
        String currencyPair = "USD/EUR";

        HistoryDBO validRecord = testRecord;
        HistoryDBO invalidRecord = new HistoryDBO(
                ObjectId.get(), 12345L, "TEST",
                "EUR/GBP", null, now.minusDays(2)
        );

        when(historyRepo.findByChatId(anyLong()))
                .thenReturn(Flux.just(validRecord, invalidRecord));

        StepVerifier.create(historyService.getHistory(
                        12345L, start, end, currencyPair
                ))
                .expectNext(validRecord)
                .verifyComplete();
    }

    @Test
    void getHistory_WithCurrencyFilter_ShouldCheckContains() {
        String currency = "USD";
        HistoryDBO validRecord = testRecord;
        HistoryDBO invalidRecord = new HistoryDBO(
                ObjectId.get(), 12345L, "TEST",
                "EUR/GBP", null, now
        );

        when(historyRepo.findByChatId(anyLong()))
                .thenReturn(Flux.just(validRecord, invalidRecord));

        StepVerifier.create(historyService.getHistory(
                        12345L, null, null, currency
                ))
                .expectNext(validRecord)
                .verifyComplete();
    }

    @Test
    void getHistory_WithoutFilters_ShouldReturnAll() {
        when(historyRepo.findByChatId(anyLong()))
                .thenReturn(Flux.just(testRecord));

        StepVerifier.create(historyService.getHistory(12345L, null, null, null))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void getFullHistory_ShouldCallFindByChatId() {
        when(historyRepo.findByChatId(anyLong()))
                .thenReturn(Flux.just(testRecord));

        StepVerifier.create(historyService.getFullHistory(12345L))
                .expectNext(testRecord)
                .verifyComplete();
    }

    @Test
    void deleteByChatId_ShouldCallRepoMethod() {
        when(historyRepo.deleteByChatId(anyLong())).thenReturn(Mono.empty());

        StepVerifier.create(historyService.deleteByChatId(12345L))
                .verifyComplete();

        verify(historyRepo).deleteByChatId(12345L);
    }

    @Test
    void getHistory_WithNullCurrencyCode_ShouldFilterOut() {
        HistoryDBO invalidRecord = new HistoryDBO(
                ObjectId.get(), 12345L, "TEST",
                null, null, now
        );

        when(historyRepo.findByChatId(anyLong()))
                .thenReturn(Flux.just(invalidRecord));

        StepVerifier.create(historyService.getHistory(
                        12345L, null, null, "USD"
                ))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void getHistory_WithExactDateMatch_ShouldInclude() {
        LocalDateTime exactTime = now;
        HistoryDBO edgeRecord = new HistoryDBO(
                ObjectId.get(), 12345L, "TEST",
                "USD/EUR", null, exactTime
        );

        when(historyRepo.findByChatId(anyLong()))
                .thenReturn(Flux.just(edgeRecord));

        StepVerifier.create(historyService.getHistory(
                        12345L, exactTime, exactTime, null
                ))
                .expectNextCount(1)
                .verifyComplete();
    }
}
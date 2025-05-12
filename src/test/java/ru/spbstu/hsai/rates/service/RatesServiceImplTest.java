package ru.spbstu.hsai.rates.service;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.rates.api.ampq.UpdateCurrenciesSDK;
import ru.spbstu.hsai.rates.api.http.dto.ExchangeRatesDTO;
import ru.spbstu.hsai.rates.dao.CurrencyDAO;
import ru.spbstu.hsai.rates.dao.CurrencyPairDAO;
import ru.spbstu.hsai.rates.entities.CurrencyDBO;
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RatesServiceImplTest {

    @Mock
    private UpdateCurrenciesSDK updateCurrenciesSDK;

    @Mock
    private CurrencyDAO currencyDAO;

    @Mock
    private CurrencyPairDAO currencyPairDAO;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private RatesServiceImpl ratesService;

    @AfterEach
    void tearDown() {
        reset(updateCurrenciesSDK, currencyDAO, currencyPairDAO, mongoTemplate);
    }

    @Test
    void calculateCrossRate_ShouldHandleDivision() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // Arrange
        CurrencyPairDBO pair = new CurrencyPairDBO(
                null, "USD", "EUR", BigDecimal.valueOf(0.9), LocalDateTime.now()
        );
        Map<String, BigDecimal> rates = Map.of(
                "USD", BigDecimal.ONE,
                "EUR", BigDecimal.valueOf(0.85)
        );

        // Act
        Method method = RatesServiceImpl.class.getDeclaredMethod("calculateCrossRate", CurrencyPairDBO.class, Map.class);
        method.setAccessible(true);

        Pair<CurrencyPairDBO, BigDecimal> result = ((Mono<Pair<CurrencyPairDBO, BigDecimal>>) method.invoke(ratesService, pair, rates)).block();

        // Assert
        assertEquals(BigDecimal.valueOf(0.85), result.getLeft().getCurrentRate().setScale(2, 2));
    }

    @Test
    void processPairUpdate_ShouldUpdateDatabase() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // Arrange
        CurrencyPairDBO pair = new CurrencyPairDBO(
                null, "USD", "EUR", BigDecimal.valueOf(0.9), LocalDateTime.now()
        );
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(CurrencyPairDBO.class))).thenReturn(Mono.empty());
        when(mongoTemplate.findOne(any(), any())).thenReturn(Mono.just(pair));

        Method method = RatesServiceImpl.class.getDeclaredMethod("processPairUpdate", Pair.class);
        method.setAccessible(true);

        // Act & Assert
        StepVerifier.create((Mono<Pair<CurrencyPairDBO, BigDecimal>>) method.invoke(ratesService, Pair.of(pair, BigDecimal.valueOf(0.8))))
                                .verifyComplete();
    }

    @Test
    void sendUpdateNotification_ShouldCalculatePercentage() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        // Arrange
        CurrencyPairDBO pair = new CurrencyPairDBO(
                null, "USD", "EUR", BigDecimal.valueOf(0.9), LocalDateTime.now()
        );
        when(updateCurrenciesSDK.sendUpdateNotification(any(), any(), any()))
                .thenReturn(Mono.empty());

        Method method = RatesServiceImpl.class.getDeclaredMethod("sendUpdateNotification", Pair.class);
        method.setAccessible(true);
        // Act & Assert
        StepVerifier.create((Mono<Pair<CurrencyPairDBO, BigDecimal>>) method.invoke(ratesService, Pair.of(pair, BigDecimal.valueOf(0.8))))
                .verifyComplete();

        verify(updateCurrenciesSDK).sendUpdateNotification(
                eq(pair),
                eq(BigDecimal.valueOf(0.8)),
                any()
                );
    }

    @Test
    void getCurrencyPairId_ShouldReturnId() {
        // Arrange
        CurrencyPairDBO pair = new CurrencyPairDBO(
                ObjectId.get(), "USD", "EUR", BigDecimal.ONE, LocalDateTime.now()
        );
        when(currencyPairDAO.findByBaseCurrencyAndTargetCurrency(anyString(), anyString()))
                .thenReturn(Mono.just(pair));

        // Act & Assert
        StepVerifier.create(ratesService.getCurrencyPairId("USD", "EUR"))
                .expectNext(pair.getCurrencyPairId())
                .verifyComplete();
    }

    @Test
    void isCurrencyExists_ShouldReturnTrueForExistingCurrency() {
        // Arrange
        when(currencyDAO.findByCode("USD")).thenReturn(Mono.just(new CurrencyDBO()));

        // Act & Assert
        StepVerifier.create(ratesService.isCurrencyExists("USD"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void getDefaultPairString_ShouldFormatPair() {
        // Arrange
        CurrencyPairDBO pair = new CurrencyPairDBO(
                ObjectId.get(), "USD", "EUR", BigDecimal.ONE, LocalDateTime.now()
        );
        when(currencyPairDAO.findById(any(ObjectId.class))).thenReturn(Mono.just(pair));

        // Act & Assert
        StepVerifier.create(ratesService.getDefaultPairString(pair.getCurrencyPairId()))
                .expectNext("USD/EUR")
                .verifyComplete();
    }
}
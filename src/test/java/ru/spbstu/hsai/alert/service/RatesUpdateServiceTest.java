package ru.spbstu.hsai.alert.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.alert.dao.AlertDAO;
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.alert.entities.RateChange;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RatesUpdateServiceTest {

    @Mock
    private AlertDAO alertDAO;

    @InjectMocks
    private RatesUpdateService ratesUpdateService;

    @Test
    void shouldTriggerAlertForAbsoluteValueAboveThreshold() {
        // Arrange
        RateChange event = new RateChange("USD", "EUR",
                new BigDecimal("1.20"), new BigDecimal("1.25"), new BigDecimal("5.0"));

        AlertDBO alert = createAlert("USD", "EUR", ">1.20");
        when(alertDAO.findByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Flux.just(alert));

        // Act & Assert
        StepVerifier.create(ratesUpdateService.processRateChange(event))
                .expectNextMatches(result ->
                        result.getReason().contains("Курс превысил 1.20") &&
                                result.getNewRate().equals(new BigDecimal("1.25"))
                )
                .verifyComplete();
    }

    @Test
    void shouldNotTriggerAlertForAbsoluteValueBelowThreshold() {
        // Arrange
        RateChange event = new RateChange("USD", "EUR",
                new BigDecimal("1.20"), new BigDecimal("1.15"), new BigDecimal("-5.0"));

        AlertDBO alert = createAlert("USD", "EUR", ">1.20");
        when(alertDAO.findByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Flux.just(alert));

        // Act & Assert
        StepVerifier.create(ratesUpdateService.processRateChange(event))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void shouldTriggerAlertForPositivePercentageChange() {
        // Arrange
        RateChange event = new RateChange("USD", "EUR",
                new BigDecimal("1.18"), new BigDecimal("1.25"), new BigDecimal("6.0"));

        AlertDBO alert = createAlert("USD", "EUR", "+5%");
        when(alertDAO.findByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Flux.just(alert));

        // Act & Assert
        StepVerifier.create(ratesUpdateService.processRateChange(event))
                .expectNextMatches(result ->
                        result.getReason().contains("Рост на 6,00%") &&
                                result.getChangePercent().equals(new BigDecimal("6.0"))
                )
                .verifyComplete();
    }

    @Test
    void shouldTriggerAlertForNegativePercentageChange() {
        // Arrange
        RateChange event = new RateChange("USD", "EUR",
                new BigDecimal("1.20"), new BigDecimal("1.15"), new BigDecimal("-4.0"));

        AlertDBO alert = createAlert("USD", "EUR", "-3%");
        when(alertDAO.findByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Flux.just(alert));

        // Act & Assert
        StepVerifier.create(ratesUpdateService.processRateChange(event))
                .expectNextMatches(result ->
                        result.getReason().contains("Падение на 4,00%") &&
                                result.getChangePercent().equals(new BigDecimal("-4.0"))
                )
                .verifyComplete();
    }

    @Test
    void shouldHandleMultipleAlerts() {
        // Arrange
        RateChange event = new RateChange("USD", "EUR",
                new BigDecimal("1.18"), new BigDecimal("1.25"), new BigDecimal("6.0"));

        AlertDBO alert1 = createAlert("USD", "EUR", ">1.20");
        AlertDBO alert2 = createAlert("USD", "EUR", "+5%");
        when(alertDAO.findByBaseCurrencyAndTargetCurrency("USD", "EUR"))
                .thenReturn(Flux.just(alert1, alert2));

        // Act & Assert
        StepVerifier.create(ratesUpdateService.processRateChange(event))
                .expectNextCount(2)
                .verifyComplete();
    }

    private AlertDBO createAlert(String base, String target, String condition) {
        AlertDBO alert = new AlertDBO();
        alert.setBaseCurrency(base);
        alert.setTargetCurrency(target);
        alert.setExpr(condition);
        alert.setChatId(12345L);
        return alert;
    }
}
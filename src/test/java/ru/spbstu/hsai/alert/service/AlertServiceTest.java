package ru.spbstu.hsai.alert.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.alert.dao.AlertDAO;
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.user.UserDTO;
import ru.spbstu.hsai.user.UserServiceSDK;
import ru.spbstu.hsai.user.UserSettings;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlertServiceTest {

    @Mock
    private AlertDAO alertDAO;

    @Mock
    private UserServiceSDK userService;

    @InjectMocks
    private AlertService alertService;

    private final Long chatId = 12345L;
    private final String alertId = "507f1f77bcf86cd799439011";

    @Test
    void addAlertSuccess() {
        // Arrange
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        UserDTO user = new UserDTO(chatId, LocalDateTime.now(), settings);
        AlertDBO newAlert = new AlertDBO(null, chatId, "USD", "EUR", ">1.2");

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(user));
        when(alertDAO.save(any(AlertDBO.class))).thenReturn(Mono.just(newAlert));

        // Act & Assert
        StepVerifier.create(alertService.addAlert(chatId, "USD/EUR", ">1.2"))
                .expectNextMatches(result ->
                        result.startsWith("✅ Уведомление добавлено") &&
                                result.contains("USD/EUR >1.2")
                )
                .verifyComplete();

        verify(alertDAO).save(any(AlertDBO.class));
    }

    @Test
    void addAlertUserNotFound() {
        // Arrange
        when(userService.getUserByChatId(chatId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertService.addAlert(chatId, "USD/EUR", ">1.2"))
                .expectErrorMatches(ex ->
                        ex instanceof CCBException &&
                                ex.getMessage().contains("Пользователь не найден")
                )
                .verify();
    }

    @Test
    void getAllAlertsByChatIdSuccess() {
        // Arrange
        UserSettings settings = new UserSettings("USD", "USD/EUR");
        UserDTO user = new UserDTO(chatId, LocalDateTime.now(), settings);
        AlertDBO alert1 = new AlertDBO(ObjectId.get(), chatId, "USD", "EUR", ">1.2");
        AlertDBO alert2 = new AlertDBO(ObjectId.get(), chatId, "GBP", "USD", "<0.8");

        when(userService.getUserByChatId(chatId)).thenReturn(Mono.just(user));
        when(alertDAO.findAllByChatId(chatId)).thenReturn(Flux.just(alert1, alert2));

        // Act & Assert
        StepVerifier.create(alertService.getAllAlertsByChatId(chatId))
                .expectNextMatches(alerts ->
                        alerts.size() == 2 &&
                                alerts.get(0).getExpr().equals(">1.2") &&
                                alerts.get(1).getExpr().equals("<0.8")
                )
                .verifyComplete();
    }

    @Test
    void getAllAlertsUserNotFound() {
        // Arrange
        when(userService.getUserByChatId(chatId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertService.getAllAlertsByChatId(chatId))
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    void deleteAlertSuccess() {
        // Arrange
        AlertDBO alert = new AlertDBO(new ObjectId(alertId), chatId, "USD", "EUR", ">1.2");

        when(alertDAO.findById(new ObjectId(alertId))).thenReturn(Mono.just(alert));
        when(alertDAO.delete(alert)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertService.deleteAlert(alertId, chatId))
                .expectNextMatches(result ->
                        result.startsWith("✅ Уведомление удалено") &&
                                result.contains("USD/EUR >1.2")
                )
                .verifyComplete();

        verify(alertDAO).delete(alert);
    }

    @Test
    void deleteAlertNotFound() {
        // Arrange
        when(alertDAO.findById(new ObjectId(alertId))).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(alertService.deleteAlert(alertId, chatId))
                .expectNext("❌ Уведомление не найдено")
                .verifyComplete();
    }

    @Test
    void deleteAlertWrongOwner() {
        // Arrange
        AlertDBO alert = new AlertDBO(new ObjectId(alertId), 9999L, "USD", "EUR", ">1.2");

        when(alertDAO.findById(new ObjectId(alertId))).thenReturn(Mono.just(alert));

        // Act & Assert
        StepVerifier.create(alertService.deleteAlert(alertId, chatId))
                .expectErrorMatches(ex ->
                        ex instanceof CCBException &&
                                ex.getMessage().contains("Уведомление не найдено")
                )
                .verify();
    }
}
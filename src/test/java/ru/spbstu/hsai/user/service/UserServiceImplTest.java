package ru.spbstu.hsai.user.service;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.user.RatesService;
import ru.spbstu.hsai.user.dao.SettingsDAO;
import ru.spbstu.hsai.user.dao.UserDAO;
import ru.spbstu.hsai.user.entities.SettingsDBO;
import ru.spbstu.hsai.user.entities.UserDBO;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserDAO userDAO;

    @Mock
    private SettingsDAO settingsDAO;

    @Mock
    private RatesService ratesService;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerUser_NewUser_CreatesUserAndSettings() {
        Long chatId = 12345L;
        ObjectId pairId = ObjectId.get();
        UserDBO newUser = new UserDBO(chatId, LocalDateTime.now());
        SettingsDBO defaultSettings = new SettingsDBO(chatId, "RUB", pairId);

        when(userDAO.findByChatId(chatId)).thenReturn(Mono.empty());
        when(userDAO.save(any())).thenReturn(Mono.just(newUser));
        when(settingsDAO.findByChatId(chatId)).thenReturn(Mono.empty());
        when(ratesService.getCurrencyPairId("RUB", "USD")).thenReturn(Mono.just(pairId));
        when(settingsDAO.save(any())).thenReturn(Mono.just(defaultSettings));

        StepVerifier.create(userService.registerUser(chatId))
                .verifyComplete();

        verify(userDAO).save(argThat(user -> user.getChatId().equals(chatId)));
        verify(settingsDAO).save(argThat(settings ->
                settings.getChatId().equals(chatId) &&
                        settings.getHomeCurrencyCode().equals("RUB")
        ));
    }

    @Test
    void setHomeCurrency_ValidCurrency_UpdatesSettings() {
        Long chatId = 12345L;
        String currency = "USD";

        // Мокируем зависимости
        when(ratesService.isCurrencyExists(currency)).thenReturn(Mono.just(true));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(SettingsDBO.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1L, 1L, null)));

        // Выполняем тестируемый метод
        StepVerifier.create(userService.setHomeCurrency(chatId, currency))
                .verifyComplete();

        // Проверяем вызовы сервисов
        verify(ratesService).isCurrencyExists(currency);

        // Используем ArgumentCaptor для захвата аргументов
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(SettingsDBO.class)
        );

        // Проверяем параметры запроса
        Query actualQuery = queryCaptor.getValue();
        assertEquals(
                Criteria.where("chatId").is(chatId).getCriteriaObject(),
                actualQuery.getQueryObject()
        );

        // Проверяем параметры обновления
        Update actualUpdate = updateCaptor.getValue();
        assertEquals(
                currency,
                ((Map<String, Object>) actualUpdate.getUpdateObject().get("$set")).get("homeCurrencyCode")
        );
    }

    @Test
    void setHomeCurrency_InvalidCurrency_ThrowsException() {
        Long chatId = 12345L;
        String currency = "INVALID";

        when(ratesService.isCurrencyExists(currency)).thenReturn(Mono.just(false));

        StepVerifier.create(userService.setHomeCurrency(chatId, currency))
                .expectErrorMatches(ex -> ex instanceof CCBException &&
                        ex.getMessage().contains("Валюта " + currency + " не поддерживается"))
                .verify();
    }

    @Test
    void setPair_ValidPair_UpdatesSettings() {
        Long chatId = 12345L;
        String base = "USD";
        String target = "EUR";
        ObjectId pairId = ObjectId.get(); // Используем String вместо ObjectId для упрощения

        when(ratesService.getCurrencyPairId(base, target)).thenReturn(Mono.just(pairId));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(SettingsDBO.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1L, 1L, null)));

        StepVerifier.create(userService.setPair(chatId, base, target))
                .verifyComplete();

        // Проверяем аргументы с помощью ArgumentCaptor
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(SettingsDBO.class)
        );

        // Проверяем критерии запроса
        Query actualQuery = queryCaptor.getValue();
        Document criteria = actualQuery.getQueryObject();
        assertEquals(chatId, criteria.get("chatId"));

        // Проверяем параметры обновления
        Update actualUpdate = updateCaptor.getValue();
        Document update = actualUpdate.getUpdateObject();
        assertEquals(pairId, ((Map<String, Object>) update.get("$set")).get("currencyPairId"));
    }

    @Test
    void setPair_InvalidPair_ThrowsException() {
        Long chatId = 12345L;
        String base = "INVALID";
        String target = "PAIR";

        when(ratesService.getCurrencyPairId(base, target)).thenReturn(Mono.empty());

        StepVerifier.create(userService.setPair(chatId, base, target))
                .expectErrorMatches(ex -> ex instanceof CCBException &&
                        ex.getMessage().contains("Валютная пара " + base + "/" + target + " не поддерживается"))
                .verify();
    }

    @Test
    void getUserSettings_ExistingSettings_ReturnsMappedSettings() {
        Long chatId = 12345L;
        SettingsDBO settingsDBO = new SettingsDBO(chatId, "USD", ObjectId.get());
        String pairString = "USD/EUR";

        when(settingsDAO.findByChatId(chatId)).thenReturn(Mono.just(settingsDBO));
        when(ratesService.getDefaultPairString(any())).thenReturn(Mono.just(pairString));

        StepVerifier.create(userService.getUserSettings(chatId))
                .expectNextMatches(settings ->
                        settings.getHomeCurrency().equals("USD") &&
                                settings.getDefaultPair().equals(pairString)
                )
                .verifyComplete();
    }

    @Test
    void getUserSettings_NoSettings_ThrowsException() {
        Long chatId = 12345L;

        when(settingsDAO.findByChatId(chatId)).thenReturn(Mono.empty());

        StepVerifier.create(userService.getUserSettings(chatId))
                .expectErrorMatches(ex -> ex instanceof CCBException &&
                        ex.getMessage().contains("Настроек пользователя не было найдено"))
                .verify();
    }
}
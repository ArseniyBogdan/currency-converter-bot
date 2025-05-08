package ru.spbstu.hsai.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.rates.RatesService;
import ru.spbstu.hsai.user.dao.SettingsDAO;
import ru.spbstu.hsai.user.dao.UserDAO;
import ru.spbstu.hsai.user.entities.SettingsDBO;
import ru.spbstu.hsai.user.entities.UserDBO;
import ru.spbstu.hsai.user.UserSettings;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl {
    private final UserDAO userDAO;
    private final SettingsDAO settingsDAO;
    private final RatesService ratesService;
    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<Void> registerUser(Long chatId){
        return userDAO.findByChatId(chatId)
                .switchIfEmpty(
                        userDAO.save(new UserDBO(
                            chatId, LocalDateTime.now()
                        )
                ))
                .then(settingsDAO.findByChatId(chatId))
                .switchIfEmpty(
                        ratesService.getCurrencyPairId("RUB", "USD").map(pairId ->
                                new SettingsDBO(
                                        chatId, "RUB", pairId
                                )
                        ).flatMap(settingsDAO::save)
                ).then();
    }

    public Mono<Void> setHomeCurrency(Long chatId, String currencyCode){
        Query query = new Query(Criteria
                .where("chatId").is(chatId)
        );
        Update update = new Update()
                .set("homeCurrencyCode", currencyCode);
        // Проверка существования валюты
        return ratesService.isCurrencyExists(currencyCode)
                .flatMap(isValid -> {
                    if (!isValid) {
                        return Mono.error(new CCBException(
                                "Валюта " + currencyCode + " не поддерживается\n" +
                                        "Список валют: /currencies"
                        ));
                    }

                    // Обновление домашней валюты
                    return mongoTemplate.updateFirst(query, update, SettingsDBO.class).then();
                });
    }

    public Mono<Void> setPair(Long chatId, String baseCurrencyCode, String targetCurrencyCode){
        // Проверка существования валюты
        return ratesService.getCurrencyPairId(baseCurrencyCode, targetCurrencyCode).switchIfEmpty(
                Mono.error(new CCBException(
                        "Валютная пара " + baseCurrencyCode + "/" + targetCurrencyCode + " не поддерживается\n" +
                                "Список валют: /currencies"
                ))
        ).flatMap(pairId -> {
            Query query = new Query(Criteria
                    .where("chatId").is(chatId)
            );
            Update update = new Update()
                    .set("currencyPairId", pairId);

            return mongoTemplate.updateFirst(query, update, SettingsDBO.class).then();
        });
    }

    public Mono<UserSettings> getUserSettings(Long chatId){
        return settingsDAO.findByChatId(chatId)
                .switchIfEmpty(Mono.error(new CCBException("Настроек пользователя не было найдено")))
                .flatMap(settings ->
                        ratesService.getDefaultPairString(settings.getCurrencyPairId())
                            .map(currencyPair ->
                                    new UserSettings(
                                            settings.getHomeCurrencyCode(),
                                            currencyPair
                                    )
                            )
        );
    }

}

package ru.spbstu.hsai.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.rates.entities.CurrencyPairDBO;
import ru.spbstu.hsai.rates.service.RatesService;
import ru.spbstu.hsai.user.dao.SettingsDAO;
import ru.spbstu.hsai.user.dao.UserDAO;
import ru.spbstu.hsai.user.entities.SettingsDBO;
import ru.spbstu.hsai.user.entities.UserDBO;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserDAO userDAO;
    private final SettingsDAO settingsDAO;
    private final RatesService RatesService;
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
                        RatesService.getCurrencyPairId("RUB", "USD").map(pairId ->
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
        return RatesService.isCurrencyExists(currencyCode)
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

}

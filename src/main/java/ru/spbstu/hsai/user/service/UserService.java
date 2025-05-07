package ru.spbstu.hsai.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
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

    public Mono<Void> registerUser(Long chatId){
        return userDAO.findById(chatId)
                .switchIfEmpty(
                        userDAO.save(new UserDBO(
                            chatId, LocalDateTime.now()
                        )
                ))
                .then(settingsDAO.findById(chatId))
                .switchIfEmpty(
                        RatesService.getCurrencyPairId("RUB", "USD").map(pairId ->
                                new SettingsDBO(
                                        chatId, "RUB", pairId
                                )
                        ).flatMap(settingsDAO::save)
                ).then();
    }

}

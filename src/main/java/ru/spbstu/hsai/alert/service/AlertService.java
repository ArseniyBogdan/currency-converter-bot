package ru.spbstu.hsai.alert.service;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.alert.dao.AlertDAO;
import ru.spbstu.hsai.alert.entities.AlertDBO;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.user.UserServiceSDK;

import java.util.List;


@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertDAO alertDAO;
    private final UserServiceSDK userService;

    public Mono<String> addAlert(Long chatId, String pair, String condition){
        return userService.getUserByChatId(chatId)
                .flatMap(user -> processAlert(pair, condition, chatId))
                .switchIfEmpty(Mono.error(new CCBException("❌ Пользователь не найден")));
    }


    private Mono<String> processAlert(String pair, String condition, Long chatId) {
        return Mono.just(pair)
                .flatMap(valid -> {
                    String[] currencies = pair.split("/");
                    AlertDBO alert = new AlertDBO(
                            null,
                            chatId,
                            currencies[0],
                            currencies[1],
                            condition
                    );
                    return alertDAO.save(alert)
                            .thenReturn("✅ Уведомление добавлено: " + formatAlert(alert));
                });
    }

    private String formatAlert(AlertDBO alert) {
        return String.format("%s/%s %s [ID: %s]",
                alert.getBaseCurrency(),
                alert.getTargetCurrency(),
                alert.getExpr(),
                alert.getId());
    }


    public Mono<List<AlertDBO>> getAllAlertsByChatId(Long chatId){
        return userService.getUserByChatId(chatId)
                .flatMapMany(user -> alertDAO.findAllByChatId(user.getChatId()))
                .collectList();
    }

    public Mono<String> deleteAlert(String alertId, Long chatId) {
        return alertDAO.findById(new ObjectId(alertId))
                .flatMap(alert -> {
                    if (!alert.getChatId().equals(chatId)) {
                        return Mono.error(new CCBException("❌ Уведомление не найдено"));
                    }
                    return alertDAO.delete(alert)
                            .thenReturn("✅ Уведомление удалено: " + formatAlert(alert));
                })
                .switchIfEmpty(Mono.just("❌ Уведомление не найдено"));
    }
}

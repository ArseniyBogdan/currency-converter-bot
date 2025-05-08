package ru.spbstu.hsai.user.dao;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.user.entities.SettingsDBO;

@Repository
public interface SettingsDAO extends ReactiveCrudRepository<SettingsDBO, Long> {
    Mono<SettingsDBO> findByChatId(Long chatId);
}

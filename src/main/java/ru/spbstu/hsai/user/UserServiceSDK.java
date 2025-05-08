package ru.spbstu.hsai.user;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserServiceSDK {
    Flux<UserDTO> getAllUsers();
    Mono<UserDTO> getUserByChatId(Long chatId);
}
package ru.spbstu.hsai.user.api.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.user.UserDTO;
import ru.spbstu.hsai.user.UserServiceSDK;
import ru.spbstu.hsai.user.dao.UserDAO;
import ru.spbstu.hsai.user.service.UserServiceImpl;

@Service
@RequiredArgsConstructor
public class UserServiceSDKImpl implements UserServiceSDK {
    private final UserServiceImpl userService;
    private final UserDAO userDAO;


    @Override
    public Flux<UserDTO> getAllUsers() {
        return userDAO.findAll().flatMap(userDBO -> {
            Long chatId = userDBO.getChatId();
            return userService.getUserSettings(chatId).map(userSettings -> new UserDTO(chatId, userDBO.getCreated(), userSettings));
        });
    }

    @Override
    public Mono<UserDTO> getUserByChatId(Long chatId) {
        return userDAO.findByChatId(chatId).flatMap(userDBO ->
            userService.getUserSettings(chatId).map(userSettings -> new UserDTO(chatId, userDBO.getCreated(), userSettings))
        );
    }
}

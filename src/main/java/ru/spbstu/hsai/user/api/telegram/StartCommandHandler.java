package ru.spbstu.hsai.user.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.service.UserServiceImpl;

@Component
@RequiredArgsConstructor
public class StartCommandHandler implements CommandHandler {
    private final UserServiceImpl userService;

    @Value("${command.start}")
    private String commandStartReply;

    @Override
    @BotCommand("/start")
    public Mono<String> handle(Message message) {
        return Mono.just(message.getChatId())
                .flatMap(chatId -> userService.registerUser(chatId)
                        .thenReturn(commandStartReply)
                        .onErrorResume(e -> Mono.error(new CCBException(
                                "Ошибка регистрации: " + e.getMessage(), e)
                        ))
                );
    }
}

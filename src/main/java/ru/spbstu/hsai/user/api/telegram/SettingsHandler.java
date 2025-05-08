package ru.spbstu.hsai.user.api.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.service.UserServiceImpl;

@Component
@RequiredArgsConstructor
public class SettingsHandler implements CommandHandler {
    private final UserServiceImpl userService;

    @Value("${command.settings}")
    private String commandSettingsReply;

    @Override
    @BotCommand("/settings")
    public Mono<String> handle(Message message) {
        return userService.getUserSettings(message.getChatId())
                .flatMap(settings -> {
                    String response = String.format(
                            commandSettingsReply,
                            settings.getHomeCurrency(),
                            settings.getDefaultPair()
                    );
                    return Mono.just(response);
                });
    }
}

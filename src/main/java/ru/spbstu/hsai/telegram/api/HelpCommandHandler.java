package ru.spbstu.hsai.telegram.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

@Component
@RequiredArgsConstructor
public class HelpCommandHandler implements CommandHandler {

    @Value("${command.help}")
    private String response;

    @Override
    @BotCommand("/help")
    public Mono<String> handle(Message message) {
        return Mono.just(response);
    }
}

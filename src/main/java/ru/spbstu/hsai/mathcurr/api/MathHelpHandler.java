package ru.spbstu.hsai.mathcurr.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

@Component
@RequiredArgsConstructor
public class MathHelpHandler implements CommandHandler {

    @Value("${command.math_help}")
    private String helpText;

    @Override
    @BotCommand("/math_help")
    public Mono<String> handle(Message message) {
        return Mono.just(helpText);
    }
}

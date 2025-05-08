package ru.spbstu.hsai.user.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.exceptions.CCBException;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.user.service.UserService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SetHomeCurrencyHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/sethome\\s+([A-Z]{3})$", Pattern.CASE_INSENSITIVE);

    private final UserService userService;

    @Value("${command.sethome}")
    private String commandSetHomeCurrencyReply;

    @Override
    @BotCommand("/sethome")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());

                    // Проверка формата команды
                    if (!matcher.matches()) {
                        return Mono.error(new CCBException(
                                "❌ Неверный формат команды. Используйте: <code>/sethome &lt;ВАЛЮТА&gt;</code>\n" +
                                        "Пример: <code>/sethome RUB</code>"
                        ));
                    }

                    String currency = matcher.group(1).toUpperCase();

                    // Проверка существования валюты
                    return userService.setHomeCurrency(message.getChatId(), currency).thenReturn(commandSetHomeCurrencyReply);
                });
    }
}

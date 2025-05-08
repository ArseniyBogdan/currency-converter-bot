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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SetPairHandler implements CommandHandler {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/setpair\\s+([A-Z]{3})/([A-Z]{3})$", Pattern.CASE_INSENSITIVE);

    private final UserServiceImpl userService;

    @Value("${command.setpair}")
    private String commandSetHomeCurrencyReply;

    @Override
    @BotCommand("/setpair")
    public Mono<String> handle(Message message) {
        return Mono.justOrEmpty(message.getText())
                .flatMap(commandText -> {
                    Matcher matcher = COMMAND_PATTERN.matcher(commandText.trim());

                    // Проверка формата команды
                    if (!matcher.matches()) {
                        return Mono.error(new CCBException(
                                "❌ Неверный формат команды. Используйте: <code>/setpair &lt;ВАЛЮТА1&gt/&lt;ВАЛЮТА2&gt;</code>\n" +
                                        "Пример: <code>/setpair RUB</code>"
                        ));
                    }

                    String currencyBase = matcher.group(1).toUpperCase();
                    String currencyTarget = matcher.group(2).toUpperCase();

                    // Проверка на одинаковые валюты
                    if (currencyBase.equals(currencyTarget)) {
                        return Mono.error(new CCBException(
                                "❌ Валюты в паре должны быть разными"
                        ));
                    }

                    // Проверка существования валюты
                    return userService.setPair(message.getChatId(), currencyBase, currencyTarget).thenReturn(commandSetHomeCurrencyReply);
                });
    }
}

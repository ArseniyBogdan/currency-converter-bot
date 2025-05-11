package ru.spbstu.hsai.mathcurr.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.HistorySDK;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;

import java.util.Map;

/**
 * Обработчик команды /math_help для получения справки по математическим операциям
 */
@Component
@RequiredArgsConstructor
public class MathHelpHandler implements CommandHandler {
    private final HistorySDK historySDK;

    @Value("${command.math_help}")
    private String helpText;

    /**
     * Обрабатывает команду /math_help, выводит справку по математических операциям
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> со справкой по математическим операциям
     */
    @Override
    @BotCommand("/math_help")
    public Mono<String> handle(Message message) {
        return Mono.just(helpText).map(result -> {
            saveHistory(message.getChatId(), message.getText(), result);
            return result;
        });
    }

    /**
     * Сохраняет историю запроса
     */
    private void saveHistory(Long chatId, String request, String result) {
        historySDK.saveHistory(
                chatId,
                "MATH_HELP",
                null,
                Map.of("request", request, "result", result)
        ).subscribe();
    }
}

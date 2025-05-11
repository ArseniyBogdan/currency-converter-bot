package ru.spbstu.hsai.history.api.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.history.service.HistoryService;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.telegram.CurrencyConverterBot;

/**
 * Обработчик команды /clear_history для очистки истории
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClearHistoryHandler implements CommandHandler {
    private final HistoryService historyService;
    private final CurrencyConverterBot bot;

    @Value("${command.clear_history.success}")
    private String successMessage;

    @Value("${command.clear_history.error}")
    private String errorMessage;

    /**
     * Обрабатывает команду /clear_history, очищает историю команд пользователей
     *
     * @param message входящее сообщение от пользователя
     * @return Mono<String> с результатом очистки истории или ошибку
     */
    @Override
    @BotCommand("/clear_history")
    public Mono<String> handle(Message message) {
        return Mono.just(message.getChatId())
                .flatMap(this::clearUserHistory)
                .thenReturn(successMessage)
                .onErrorResume(e -> {
                    log.error("History cleanup failed", e);
                    bot.sendMessage(message.getChatId(), errorMessage);
                    return Mono.empty();
                });
    }

    private Mono<Void> clearUserHistory(Long chatId) {
        return historyService.deleteByChatId(chatId)
                .doOnSuccess(__ -> log.info("History cleared for chat: {}", chatId))
                .doOnError(e -> log.error("Error clearing history for chat: {}", chatId, e));
    }
}
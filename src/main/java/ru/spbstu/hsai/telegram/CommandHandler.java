package ru.spbstu.hsai.telegram;

import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;

public interface CommandHandler {
    Mono<String> handle(Message message);
}

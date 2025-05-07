package ru.spbstu.hsai.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CommandDispatcher {
    private final Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();

    public void registerHandler(String command, CommandHandler handler) {
        handlers.put(command, handler);
    }

    public Mono<String> dispatch(Message message) {
        return Mono.fromSupplier(message::getText)
                .flatMap(text -> {
                    String command = text.split(" ")[0].toLowerCase();
                    return Mono.justOrEmpty(handlers.get(command))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown command: " + command)));
                })
                .flatMap(handler -> handler.handle(message));
    }
}

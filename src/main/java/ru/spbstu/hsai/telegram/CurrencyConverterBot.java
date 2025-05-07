package ru.spbstu.hsai.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CurrencyConverterBot extends TelegramLongPollingBot {
    private final CommandDispatcher dispatcher;

    @Value("${bot_username}")
    private final String botUsername;

    @Value("${command.unknown-command}")
    private String unknownCommandReply;

    @Value("${command.unknown-message}")
    private String unknownMessageReply;

    @Value("${command.error}")
    private String errorCommandReply;

    @Autowired
    public CurrencyConverterBot(
            CommandDispatcher dispatcher,
            @Value("${bot_username}") String botUsername,
            @Value("${bot_token}") String botToken
    ){
        super(botToken);
        this.dispatcher = dispatcher;
        this.botUsername = botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()){
            Message message = update.getMessage();
            long chatId = message.getChatId();
            String text = message.getText();

            dispatcher.dispatch(message)
                    .map(response -> {
                        // Если dispatcher вернул ответ
                        sendMessage(chatId, response);
                        return response;
                    })
                    .switchIfEmpty(Mono.fromRunnable(() -> {
                        // Если команда не распознана (пустой Mono)
                        sendMessage(chatId, unknownCommandReply);
                    }))
                    .onErrorResume(e -> {
                        // Обработка ошибок
                        log.error("Error processing message: {}", text, e);
                        sendMessage(chatId, errorCommandReply);
                        return Mono.empty();
                    })
                    .subscribe(); // Важно: активируем выполнение цепочки
        } else {
            sendMessage(
                    update.getMessage().getChatId(),
                    unknownMessageReply
            );
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");
        try{
            execute(message);
        } catch (TelegramApiException e){
            log.error("Error while send message to user", e);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
}

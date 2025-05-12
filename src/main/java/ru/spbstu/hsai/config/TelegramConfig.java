package ru.spbstu.hsai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.spbstu.hsai.telegram.BotCommand;
import ru.spbstu.hsai.telegram.CommandDispatcher;
import ru.spbstu.hsai.telegram.CommandHandler;
import ru.spbstu.hsai.telegram.CurrencyConverterBot;

@Configuration
@RequiredArgsConstructor
@PropertySource(value = "classpath:command.properties", encoding = "UTF-8")
public class TelegramConfig {
    private final ApplicationContext context;
    private final CommandDispatcher dispatcher;

    @Bean
    public TelegramBotsApi telegramBotsApi(CurrencyConverterBot bot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        bot.getOptions().setMaxThreads(100);
        api.registerBot(bot);
        return api;
    }

    @PostConstruct
    public void init() {
        context.getBeansOfType(CommandHandler.class).forEach((name, handler) -> {
            BotCommand annotation = null;
            try {
                annotation = handler.getClass()
                        .getMethod("handle", Message.class)
                        .getAnnotation(BotCommand.class);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            if (annotation != null) {
                dispatcher.registerHandler(annotation.value(), handler);
            }
        });
    }
}

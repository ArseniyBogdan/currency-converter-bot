package ru.spbstu.hsai.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public CachingConnectionFactory rabbitConnectionFactory(
            @Value("${rabbitmq_host}") String host,
            @Value("${rabbitmq_username}") String username,
            @Value("${rabbitmq_password}") String password
    ) {
        CachingConnectionFactory factory = new CachingConnectionFactory(host);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

    // Для управления объявлением очередей/обменников
    @Bean
    public AmqpAdmin amqpAdmin(CachingConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // Объявление обменника для обновления курсов валют
    @Bean
    public DirectExchange currencyConverterBotExchange() {
        return new DirectExchange("currency-converter-bot", true, false);
    }

    // Объявление очереди под обновление курсов
    @Bean
    public Queue currencyUpdatesQueue() {
        return new Queue("currency-converter-bot.rates-updates", true, false, false);
    }

    // Объявление очереди под задачи экспорта
    @Bean
    public Queue exportQueue() {
        return new Queue("currency-converter-bot.export-tasks", true, false, false);
    }

    // Объявление очереди под обновление истории пользователей
    @Bean
    public Queue historyQueue() {
        return new Queue("currency-converter-bot.history-save", true, false, false);
    }

    // Привязка очереди к обменнику
    @Bean
    public Binding currencyUpdatesBinding() {
        return BindingBuilder
                .bind(currencyUpdatesQueue())
                .to(currencyConverterBotExchange())
                .with("rates.update");
    }

    @Bean
    public Binding exportBinding() {
        return BindingBuilder
                .bind(exportQueue())
                .to(currencyConverterBotExchange())
                .with("export.task");
    }

    @Bean
    public Binding historyBinding() {
        return BindingBuilder
                .bind(historyQueue())
                .to(currencyConverterBotExchange())
                .with("history.save");
    }

}

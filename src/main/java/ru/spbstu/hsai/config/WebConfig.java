package ru.spbstu.hsai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;

@Configuration
@EnableWebFlux
@PropertySource("classpath:application.properties")
public class WebConfig implements WebFluxConfigurer {

    @Value("${bot_token}")
    private String botToken;

    @Bean
    public WebFilter requestLogger() {
        return (exchange, chain) -> {
            System.out.println("Incoming request: " + exchange.getRequest().getURI());
            return chain.filter(exchange);
        };
    }

    // WebClient для Telegram API
    @Bean("telegramWebClient")
    public WebClient telegramWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.telegram.org" + "/bot" + botToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // WebClient для OpenExchangeRates
    @Bean("exchangeRatesWebClient")
    public WebClient exchangeRatesWebClient() {
        return WebClient.builder()
                .baseUrl("https://openexchangerates.org/api")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}

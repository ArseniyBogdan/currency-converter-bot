package ru.spbstu.hsai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;

@Configuration
@EnableWebFlux
@EnableScheduling
@Import({
        org.springdoc.core.SpringDocConfiguration.class,             // Основная конфигурация ядра
        org.springdoc.webflux.core.SpringDocWebFluxConfiguration.class, // Конфигурация для WebFlux
        org.springdoc.core.SwaggerUiConfigProperties.class,         // Bean, который ищет Spring! Возможно, его нужно импортировать напрямую
        org.springdoc.core.SwaggerUiOAuthProperties.class,          // Конфигурация OAuth для UI
        org.springdoc.webflux.ui.SwaggerConfig.class                // Конфигурация UI для WebFlux
})
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Настройка для статических ресурсов Swagger UI WebJars
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // Настройка для index.html Swagger UI
        registry.addResourceHandler("/swagger-ui.html") // Или "/swagger-ui/**" в зависимости от версии springdoc и желаемого URL
                .addResourceLocations("classpath:/META-INF/resources/");

        // Убедитесь, что эти пути соответствуют тому, что упаковано в webjars/swagger-ui
        // Часто используется "/swagger-ui/index.html" как точка входа
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/5.11.8/");

        registry.addResourceHandler("/openapi.yml") // URL-путь, по которому будет доступен файл
                .addResourceLocations("classpath:/static/");// Директория на classpath, где лежит файл

    }
}

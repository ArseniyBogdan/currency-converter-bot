package ru.spbstu.hsai.rates.api.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.rates.api.http.dto.ExchangeRatesDTO;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;

import java.util.Map;

/**
 * Сервис для периодического обновления валютных курсов из OpenExchangeRates API
 */
@Service
@Slf4j
public class OpenExchangeRatesSDK {
    private final WebClient webClient;
    private final String apiKey;

    public OpenExchangeRatesSDK(
            @Qualifier("telegramWebClient") WebClient webClient,
            RatesServiceImpl service,
            @Value("${api_key}") String apiKey
    ){
        this.webClient = webClient;
        this.apiKey = apiKey;
    }

    public Mono<Map<String, String>> fetchCurrencies() {
        return webClient.get()
                .uri("https://openexchangerates.org/api/currencies.json?app_id={key}", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .retry(3);
    }

    public Mono<ExchangeRatesDTO> fetchExchangeRates() {
        return webClient.get()
                .uri("https://openexchangerates.org/api/latest.json?app_id={key}", apiKey)
                .retrieve()
                .bodyToMono(ExchangeRatesDTO.class)
                .retry(3);
    }
}

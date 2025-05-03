package ru.spbstu.hsai.rates.api.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.rates.api.http.dto.ExchangeRatesDTO;
import ru.spbstu.hsai.rates.service.RatesService;

import java.util.Map;

/**
 * Сервис для периодического обновления валютных курсов из OpenExchangeRates API
 */
@Service
@Slf4j
public class RatesFetcher {
    private final WebClient webClient;
    private final RatesService service;
    private final String apiKey;

    public RatesFetcher(
            @Qualifier("telegramWebClient") WebClient webClient,
            RatesService service,
            @Value("${api_key}") String apiKey
    ){
        this.webClient = webClient;
        this.service = service;
        this.apiKey = apiKey;
    }

    @Scheduled(fixedRate = 3600000)
    public void scheduleCurrencyUpdate() {
        Mono.just(apiKey)
                .flatMap(this::fetchCurrencies)
                .flatMap(service::updateCurrencyData)
                .then(Mono.just(apiKey))
                .flatMap(this::fetchExchangeRates)
                .flatMap(service::updateCurrencyPairs).subscribe(
                        null,
                        ex -> log.error("Currency update failed", ex),
                        () -> log.info("Currency update completed")
                );
    }

    private Mono<Map<String, String>> fetchCurrencies(String apiKey) {
        return webClient.get()
                .uri("https://openexchangerates.org/api/currencies.json?app_id={key}", apiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .retry(3);
    }

    private Mono<ExchangeRatesDTO> fetchExchangeRates(String apiKey) {
        return webClient.get()
                .uri("https://openexchangerates.org/api/latest.json?app_id={key}", apiKey)
                .retrieve()
                .bodyToMono(ExchangeRatesDTO.class)
                .retry(3);
    }
}

package ru.spbstu.hsai.rates;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.spbstu.hsai.rates.api.http.OpenExchangeRatesSDK;
import ru.spbstu.hsai.rates.service.RatesServiceImpl;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Slf4j
@Service
public class RatesFetcher {
    private final RatesServiceImpl service;
    private final OpenExchangeRatesSDK openExchangeRatesSDK;

    private static final String UPDATE_JOB_KEY = "currencyUpdateJob";
    private final ConcurrentHashMap<String, Boolean> activeJobs = new ConcurrentHashMap<>();

    @Scheduled(cron = "0 0 * * * *") // Каждый час в 00 минут
    public void scheduleCurrencyUpdate() {
        executeCurrencyUpdate()
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
                .timeout(Duration.ofMinutes(10))
                .subscribe();
    }

    public Mono<Void> executeCurrencyUpdate() {
        if (activeJobs.putIfAbsent(UPDATE_JOB_KEY, true) != null) {
            log.warn("Currency update is already in progress");
            return Mono.empty();
        }

        log.info("Currency update started");
        return openExchangeRatesSDK.fetchCurrencies()
                .flatMap(service::updateCurrencyData)
                .then(openExchangeRatesSDK.fetchExchangeRates())
                .flatMap(service::updateCurrencyPairs)
                .doOnSuccess(v -> log.info("Currency update completed"))
                .doOnError(ex -> log.error("Currency update failed", ex))
                .doFinally(signal -> activeJobs.remove(UPDATE_JOB_KEY));
    }
}

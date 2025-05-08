package ru.spbstu.hsai.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import reactor.core.publisher.Mono;
import ru.spbstu.hsai.admin.ApiKeyAuthenticationToken;
import ru.spbstu.hsai.admin.ApiKeyInitializer;
import ru.spbstu.hsai.admin.ApiKeyService;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@Slf4j
@Import(MongoConfig.class)
public class SecurityConfig {

    private final ApplicationContext context;

    public SecurityConfig(ApplicationContext context) {
        this.context = context;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            AuthenticationWebFilter apiKeyAuthenticationFilter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/users/**").hasAuthority("ROLE_ADMIN")
                        .pathMatchers("/**").permitAll()
                        .anyExchange().authenticated()
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .addFilterAt(apiKeyAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public AuthenticationWebFilter apiKeyAuthenticationFilter(ReactiveAuthenticationManager authenticationManager) {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(authenticationManager);
        filter.setServerAuthenticationConverter(exchange -> {
            String header = exchange.getRequest()
                    .getHeaders()
                    .getFirst("Authorization");

            if (header == null || !header.startsWith("ApiKey ")) {
                return Mono.empty();
            }

            String apiKey = header.substring(7);
            return Mono.just(new ApiKeyAuthenticationToken(apiKey));
        });
        return filter;
    }

    @Bean
    public ReactiveAuthenticationManager authenticationManager(ApiKeyService apiKeyService) {
        return authentication -> {
            String apiKey = (String) authentication.getCredentials();

            return apiKeyService.checkKeyRevoked(apiKey)
                    .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid API Key")))
                    .flatMap(revoked -> {
                        if (revoked) {
                            return Mono.error(new DisabledException("API Key revoked"));
                        }
                        return Mono.just(new ApiKeyAuthenticationToken(
                                apiKey,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        ));
                    });
        };
    }

    @PostConstruct
    public void initApiKeys(){
        context.getBean(ApiKeyInitializer.class).run();
    }
}

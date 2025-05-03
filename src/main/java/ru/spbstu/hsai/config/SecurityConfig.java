//package ru.spbstu.hsai.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
//import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
//import org.springframework.security.config.web.server.ServerHttpSecurity;
//import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.server.SecurityWebFilterChain;
//
//@Configuration
//@EnableWebFluxSecurity
//@EnableReactiveMethodSecurity
//public class SecurityConfig {
//
////    @Bean
////    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
////        return http
////                .csrf(ServerHttpSecurity.CsrfSpec::disable)
////                .authorizeExchange(exchanges -> exchanges
////                        .pathMatchers("/actuator/health").permitAll()
////                        .pathMatchers("/api/public/**").permitAll()
////                        .pathMatchers("/rabbitmq/**").hasRole("ADMIN")
////                        .anyExchange().authenticated()
////                )
////                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
////                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
////                .build();
////    }
//
//    @Bean
//    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
//        return http
//                .authorizeExchange(ex -> ex
//                        .anyExchange().hasAnyRole("USER", "ADMIN")
//                )
//                .anonymous(anonymous -> anonymous
//                        .authorities("ROLE_USER")
//                )
//                .build();
//    }
//
//    @Bean
//    public MapReactiveUserDetailsService userDetailsService(PasswordEncoder encoder) {
//        // TODO тут надо обладать доступом к хранилищу пользователей
//        UserDetails user = User.builder()
//                .username("user")
//                .password(encoder.encode("password"))
//                .roles("USER") // Роль USER
//                .build();
//
//        // TODO тут надо обладать доступом к хранилищу админов
//        UserDetails admin = User.builder()
//                .username("admin")
//                .password(encoder.encode("admin"))
//                .roles("ADMIN", "USER") // Две роли: ADMIN и USER
//                .build();
//
//        return new MapReactiveUserDetailsService(user, admin);
//    }
//
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
//}

package ru.spbstu.hsai;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.modulith.Modulithic;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.http.server.HttpServer;
import ru.spbstu.hsai.config.VaultConfig;

@Modulithic
public class Application {
    public static void main(String[] args) {
        // 1. Создаем Spring контекст
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(VaultConfig.class); // Регистрируем конфигурацию
        context.refresh();

        // 2. Создаем HTTP обработчик WebFlux
        HttpHandler handler = WebHttpHandlerBuilder
                .applicationContext(context)
                .build();

        // 3. Адаптируем для Netty
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(handler);

        // 4. Запускаем Netty сервер
        HttpServer.create()
                .host("0.0.0.0")
                .port(8081)
                .handle(adapter)
                .bindNow()
                .onDispose()
                .block();
    }
}
# === Stage 1: Build (если нужно собирать на этапе Docker) ===
# Но у нас fatJar уже собран в Jenkins, поэтому просто копируем его

# === Stage 2: Runtime ===
# Используем Eclipse Temurin Java 23 (как в вашем playbook)
FROM eclipse-temurin:23-jre-alpine

# Метаданные
LABEL maintainer="shklyarova"
LABEL description="Currency Converter Bot - Kubernetes deployment"

# Рабочая директория
WORKDIR /app

# Копируем fatJar (имя должно совпадать с вашим build.gradle.kts)
COPY build/libs/currency-converter-bot-0.0.jar app.jar

# Открываем порт (из deploy.yml: health на 8081)
EXPOSE 8081

# Переменные окружения по умолчанию (можно переопределить в K8s)
ENV SPRING_APPLICATION_NAME=currency-converter-bot
ENV SERVER_PORT=8081
ENV SPRING_CLOUD_VAULT_SCHEME=http
ENV SPRING_CLOUD_VAULT_KV_ENABLED=true
ENV SPRING_CLOUD_VAULT_KV_BACKEND=secret
ENV SPRING_CLOUD_VAULT_AUTHENTICATION=TOKEN

# Запускаем приложение
# -Dspring.cloud.vault.token можно передать через env в K8s
ENTRYPOINT ["java", "-jar", "app.jar"]


#FROM openjdk:24
#WORKDIR /app
## The application's jar file
#ARG JAR_FILE=./build/libs/currency-converter-bot-0.0.jar
## Add the application's jar to the container
#ADD ${JAR_FILE} /app.jar
#
## HTTP port
#EXPOSE 8081
#
#ENTRYPOINT ["java", "-jar", "/app.jar"]
FROM openjdk:23

ARG SPRING_CLOUD_VAULT_URI_FULL
ARG SPRING_CLOUD_VAULT_URI
ARG SPRING_CLOUD_VAULT_PORT
ARG SPRING_CLOUD_VAULT_TOKEN
ARG SPRING_CLOUD_VAULT_SCHEME
ARG SPRING_CLOUD_VAULT_KV_ENABLED
ARG SPRING_CLOUD_VAULT_KV_BACKEND
ARG SPRING_CLOUD_VAULT_AUTHENTICATION
ARG SPRING_APPLICATION_NAME

ENV SPRING_CLOUD_VAULT_URI_FULL=${SPRING_CLOUD_VAULT_URI_FULL}
ENV SPRING_CLOUD_VAULT_URI=${SPRING_CLOUD_VAULT_URI}
ENV SPRING_CLOUD_VAULT_PORT=${SPRING_CLOUD_VAULT_PORT}
ENV SPRING_CLOUD_VAULT_TOKEN=${SPRING_CLOUD_VAULT_TOKEN}
ENV SPRING_CLOUD_VAULT_SCHEME=${SPRING_CLOUD_VAULT_SCHEME}
ENV SPRING_CLOUD_VAULT_KV_ENABLED=${SPRING_CLOUD_VAULT_KV_ENABLED}
ENV SPRING_CLOUD_VAULT_KV_BACKEND=${SPRING_CLOUD_VAULT_KV_BACKEND}
ENV SPRING_CLOUD_VAULT_AUTHENTICATION=${SPRING_CLOUD_VAULT_AUTHENTICATION}
ENV SPRING_APPLICATION_NAME=${SPRING_APPLICATION_NAME}

# The application's jar file
ARG JAR_FILE=./build/libs/currency-converter-bot-0.0.jar
# Add the application's jar to the container
ADD ${JAR_FILE} /app.jar

# HTTP port
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app.jar"]
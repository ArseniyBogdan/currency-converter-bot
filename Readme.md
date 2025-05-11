# Currency Converter Bot (Server)
Серверное приложение для Telegram-бота конвертера валют с использованием Spring Framework, MongoDB, RabbitMQ и HashiCorp Vault.
Телеграм-бот позволяет пользователям получать актуальные курсы валют, конвертировать суммы, настраивать уведомления и просматривать историю запросов.

## Технологический стек
- Язык программирования: Java 23.
- Фреймворк: Spring 6.2.3 (WebFlux, JPA, Modulith, AMQP, Security, Vault).
- Telegram API: Telegrambots.
- Request mapping: Jackson
- Брокер сообщений: RabbitMQ.
- База данных: MongoDB.
- Контейнеризация: Docker, Docker Compose.
- Логирование: SLF4J, Log4j
- Тестирование: JUnit, Mockito,  
- Дополнительные зависимости: Project Lombok

## Требования

- Docker 4.0+
- Docker Compose 2.20+
- Java 23+
- Gradle 8.12+

## Настройка окружения

1. Создайте файл `.env` в корне проекта:
```
# MongoDB
MONGO_ROOT_USER=admin
MONGO_ROOT_PASSWORD=password

# RabbitMQ
RABBITMQ_DEFAULT_USER=admin
RABBITMQ_DEFAULT_PASS=password

# Vault
SPRING_CLOUD_VAULT_URI=http://vault:8200
SPRING_CLOUD_VAULT_TOKEN=root
SPRING_CLOUD_VAULT_SCHEME=http
SPRING_CLOUD_VAULT_KV_ENABLED=true
SPRING_CLOUD_VAULT_KV_BACKEND=secret
SPRING_CLOUD_VAULT_AUTHENTICATION=token
SPRING_APPLICATION_NAME=currency-converter-bot
```


## Запуск приложения
### 1. Сборка проекта
Выполните команду в корне проекта:
```bash
./gradlew.bat fatJar
```

### 2. Создание образа 
Из корня проекта следует выполнить следующую команду:
```bash
docker build . -t your-dockerhub-username/currency-converter-bot:your-tag
```

### 3. Авторизация в dockerhub
```bash
docker login -u your-dockerhub-username
```

### 4. Загрузка образа на dockerhub
```bash
docker push your-dockerhub-username/currency-converter-bot:your-tag
```

### 5. Создание скрипта инициализации
Требуется, чтобы существовал файл ./vault/scripts/init-vault.sh:
```bash
#!/bin/sh

# Ждём, пока Vault станет доступен и проинициализирован
until vault status -format=json | grep -q '"initialized": true'; do
  echo "Waiting for Vault to initialize..."
  sleep 2
done

# Записываем секреты в Vault
vault kv put secret/currency-converter-bot \
  mongo_uri="mongodb://mongo:27017" \
  mongo_username="admin" \
  mongo_password="password" \
  rabbitmq_host="rabbitmq" \
  rabbitmq_username="admin" \
  rabbitmq_password="password" \
  bot_token="your-tokeng" \
  bot_username="CurrencyConverterBot" \
  api_key="your-api-key" 

echo "Secrets loaded into Vault:"
vault kv get secret/currency-converter-bot

# Активируем Transit
vault secrets enable transit

# Создаем ключ
vault write -f transit/keys/api-keys \
      type=aes256-gcm96 \
      derived=true \
      convergent_encryption=true

echo "Transit Engine активирован"
```
Этот файл отвечает за инициализацию vault-а секретами.
### 6. Запуск docker-compose
Для начала запустите только контейнеры с Vault, vault-init, MongoDB и RabbitMQ:
```bash
docker-compose up -d vault mongo rabbitmq vault-init
```
Дождитесь, пока не поднимутся все сервисы в течение 10 секунд.
После запускайте основное приложение:
```bash
docker-compose up -d app
```
#!/bin/bash
# =============================================================================
# setup-docker-env.sh — Подготовка окружения для docker compose
# Цель: после запуска скрипта достаточно выполнить "docker compose up"
# =============================================================================

set -euo pipefail

readonly LOG_FILE="/var/log/setup-docker-env.log"
log() { 
    echo "[$(date '+%H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

# Запуск от root
if [[ $EUID -ne 0 ]]; then
    echo "❌ Запустите от root: sudo $0" >&2
    exit 1
fi

log "🚀 Начало настройки окружения..."

# =============================================================================
# 1. Базовые утилиты
# =============================================================================
log "📦 Установка базовых пакетов..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq \
    curl wget git jq unzip \
    gnupg ca-certificates \
    apt-transport-https \
    software-properties-common \
    lsb-release

# =============================================================================
# 2. Docker Engine
# =============================================================================
log "🐳 Установка Docker..."

# Удаляем старые версии если есть
apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

# Официальный репозиторий Docker
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Docker: старт + автозагрузка
systemctl enable --now docker

log "✅ Docker: $(docker --version)"
log "✅ Docker Compose: $(docker compose version)"

# =============================================================================
# 3. Java (опционально — если нужно для локальной сборки)
# =============================================================================
log "☕ Установка Java 21 (LTS)..."

curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" | tee /etc/apt/sources.list.d/adoptium.list

apt-get update -qq
apt-get install -y -qq temurin-21-jdk

# JAVA_HOME
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
echo "JAVA_HOME=${JAVA_HOME}" >> /etc/environment

log "✅ Java: $(java -version 2>&1 | head -1)"

# =============================================================================
# 4. Пользователь для Docker (без sudo)
# =============================================================================
log "👤 Настройка доступа к Docker..."

# Создаём пользователя botuser если нет
if ! id -u botuser &>/dev/null; then
    useradd -m -s /bin/bash botuser
fi

# Добавляем в группу docker
usermod -aG docker botuser

# Копируем SSH-ключи если есть (для git clone)
if [[ -d /home/ubuntu/.ssh ]]; then
    mkdir -p /home/botuser/.ssh
    cp -r /home/ubuntu/.ssh/* /home/botuser/.ssh/ 2>/dev/null || true
    chmod 700 /home/botuser/.ssh
    chmod 600 /home/botuser/.ssh/* 2>/dev/null || true
    chown -R botuser:botuser /home/botuser/.ssh
fi

log "✅ Пользователь 'botuser' добавлен в группу docker"

# =============================================================================
# 5. Подготовка директории для проекта
# =============================================================================
log "📁 Подготовка рабочей директории..."

APP_DIR="/opt/currency-converter-bot"
mkdir -p "${APP_DIR}"
chown -R botuser:botuser "${APP_DIR}"

log "✅ Директория готова: ${APP_DIR}"

log "🎉 Настройка завершена!"
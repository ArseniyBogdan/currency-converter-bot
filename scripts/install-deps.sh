#!/bin/bash
# =============================================================================
# setup-docker-env.sh — Подготовка окружения для currency-converter-bot
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
    lsb-release \
    xfsprogs

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
# 3. 🗄️ Монтирование Cinder-томов для MongoDB и RabbitMQ
# =============================================================================
log "💾 Настройка томов для данных..."

# Функция для безопасного монтирования тома
mount_volume() {
    local device="$1"
    local mount_point="$2"
    local owner="$3"
    local group="$4"
    
    log "🔍 Проверка устройства ${device}..."
    
    # Если устройство не существует — пропускаем (том может быть не подключён)
    if [[ ! -b "${device}" ]]; then
        log "⚠️  Устройство ${device} не найдено, пропускаем монтирование"
        return 0
    fi
    
    # Проверяем, смонтировано ли уже
    if mountpoint -q "${mount_point}" 2>/dev/null; then
        log "✅ ${mount_point} уже смонтирован"
        return 0
    fi
    
    # Проверяем, есть ли файловая система (чтобы не отформатировать существующие данные)
    if ! blkid "${device}" | grep -q "TYPE="; then
        log "📀 Форматирование ${device} в ext4..."
        mkfs.ext4 -F "${device}"
    else
        log "✅ На ${device} уже есть файловая система"
    fi
    
    # Создаём точку монтирования
    mkdir -p "${mount_point}"
    
    # Монтируем
    mount "${device}" "${mount_point}"
    log "✅ Смонтировано: ${device} → ${mount_point}"
    
    # Добавляем в fstab для персистентности (если ещё нет)
    if ! grep -q "${device}" /etc/fstab; then
        echo "${device} ${mount_point} ext4 defaults,nofail 0 2" >> /etc/fstab
        log "✅ Добавлено в /etc/fstab"
    fi
    
    # Создаём пользователя/группу если нужно и устанавливаем права
    if ! id -u "${owner}" &>/dev/null; then
        useradd -r -s /usr/sbin/nologin "${owner}" 2>/dev/null || true
    fi
    chown -R "${owner}:${group}" "${mount_point}"
    chmod 750 "${mount_point}"
    log "✅ Права установлены: ${owner}:${group} на ${mount_point}"
}

# 🔹 MongoDB: /dev/vdb → /var/lib/mongodb
mount_volume "/dev/vdb" "/var/lib/mongodb" "mongodb" "mongodb"

# 🔹 RabbitMQ: /dev/vdc → /var/lib/rabbitmq  
mount_volume "/dev/vdc" "/var/lib/rabbitmq" "rabbitmq" "rabbitmq"

usermod -aG docker ubuntu

# =============================================================================
# 6. 📁 Подготовка директории для проекта
# =============================================================================
log "📁 Подготовка рабочей директории..."

APP_DIR="/opt/currency-converter-bot"
mkdir -p "${APP_DIR}"
chown -R botuser:botuser "${APP_DIR}"

log "✅ Директория готова: ${APP_DIR}"
#!/bin/bash
# =============================================================================
# подготовка окружения для currency-converter-bot
# После выполнения достаточно запустить: docker compose up
#
# Делает:
#   1) базовые утилиты
#   2) Docker + docker compose plugin
#   3) Java 23 (Temurin, скачивание архива)
#   4) монтирование Cinder-томов:
#        /dev/vdb -> /var/lib/mongodb
#        /dev/vdc -> /var/lib/rabbitmq
#   5) пользователь botuser в группе docker
#   6) директория /opt/currency-converter-bot
# =============================================================================

set -euo pipefail

readonly LOG_FILE="/var/log/setup-docker-env.log"
log() {
    echo "[$(date '+%H:%M:%S')] $*" | tee -a "${LOG_FILE}"
}

# Требуется root
if [[ $EUID -ne 0 ]]; then
    echo "Run as root: sudo $0" >&2
    exit 1
fi

log "Start environment setup"

# -----------------------------------------------------------------------------
# 1) Base packages
# -----------------------------------------------------------------------------
log "Install base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq \
    curl wget git jq unzip \
    gnupg ca-certificates \
    apt-transport-https \
    software-properties-common \
    lsb-release \
    xfsprogs ext4

# -----------------------------------------------------------------------------
# 2) Docker Engine
# -----------------------------------------------------------------------------
log "Install Docker"

apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
    tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin

systemctl enable --now docker

log "Docker: $(docker --version)"
log "Docker Compose: $(docker compose version)"

# -----------------------------------------------------------------------------
# 3) Java 23 (Temurin)
# -----------------------------------------------------------------------------
log "Install Java 23"

JAVA_VERSION="23.0.2+7"
JAVA_BUILD="23.0.2+7"
JAVA_FILENAME="OpenJDK23U-jdk_x64_linux_hotspot_${JAVA_BUILD}.tar.gz"
JAVA_URL="https://github.com/adoptium/temurin23-binaries/releases/download/jdk-${JAVA_VERSION}/${JAVA_FILENAME}"
JAVA_INSTALL_DIR="/opt/java/temurin-23"

mkdir -p "${JAVA_INSTALL_DIR}"

if [[ ! -f "${JAVA_INSTALL_DIR}/bin/java" ]]; then
    log "Download Java: ${JAVA_URL}"
    wget --progress=bar:force -O "/tmp/${JAVA_FILENAME}" "${JAVA_URL}"

    log "Extract Java"
    tar -xzf "/tmp/${JAVA_FILENAME}" -C "${JAVA_INSTALL_DIR}" --strip-components=1
    rm -f "/tmp/${JAVA_FILENAME}"

    echo "JAVA_HOME=${JAVA_INSTALL_DIR}" >> /etc/environment
    echo "PATH=\${JAVA_HOME}/bin:\${PATH}" >> /etc/environment
    export JAVA_HOME="${JAVA_INSTALL_DIR}"

    log "Java installed to ${JAVA_INSTALL_DIR}"
else
    log "Java already present, skip"
fi

log "Java: $(${JAVA_INSTALL_DIR}/bin/java -version 2>&1 | head -1)"

# -----------------------------------------------------------------------------
# 4) Mount Cinder volumes
# -----------------------------------------------------------------------------
log "Configure data volumes"

mount_volume() {
    local device="$1"
    local mount_point="$2"
    local owner="$3"
    local group="$4"

    log "Check device ${device}"

    if [[ ! -b "${device}" ]]; then
        log "Device not found, skip: ${device}"
        return 0
    fi

    if mountpoint -q "${mount_point}" 2>/dev/null; then
        log "Already mounted: ${mount_point}"
        return 0
    fi

    if ! blkid "${device}" | grep -q "TYPE="; then
        log "Format ${device} as ext4"
        mkfs.ext4 -F "${device}"
    else
        log "Filesystem already exists on ${device}"
    fi

    mkdir -p "${mount_point}"
    mount "${device}" "${mount_point}"
    log "Mounted ${device} -> ${mount_point}"

    if ! grep -q "${device}" /etc/fstab; then
        echo "${device} ${mount_point} ext4 defaults,nofail 0 2" >> /etc/fstab
        log "Added to /etc/fstab"
    fi

    if ! id -u "${owner}" &>/dev/null; then
        useradd -r -s /usr/sbin/nologin "${owner}" 2>/dev/null || true
    fi

    chown -R "${owner}:${group}" "${mount_point}"
    chmod 750 "${mount_point}"
    log "Permissions set: ${owner}:${group} ${mount_point}"
}

mount_volume "/dev/vdb" "/var/lib/mongodb" "mongodb" "mongodb"
mount_volume "/dev/vdc" "/var/lib/rabbitmq" "rabbitmq" "rabbitmq"

# -----------------------------------------------------------------------------
# 5) Docker user
# -----------------------------------------------------------------------------
log "Configure docker user"

if ! id -u botuser &>/dev/null; then
    useradd -m -s /bin/bash botuser
fi

usermod -aG docker botuser

if [[ -d /home/ubuntu/.ssh ]]; then
    mkdir -p /home/botuser/.ssh
    cp -r /home/ubuntu/.ssh/* /home/botuser/.ssh/ 2>/dev/null || true
    chmod 700 /home/botuser/.ssh
    chmod 600 /home/botuser/.ssh/* 2>/dev/null || true
    chown -R botuser:botuser /home/botuser/.ssh
fi

log "User botuser is in docker group"

# -----------------------------------------------------------------------------
# 6) Project directory
# -----------------------------------------------------------------------------
log "Prepare project directory"

APP_DIR="/opt/currency-converter-bot"
mkdir -p "${APP_DIR}"
chown -R botuser:botuser "${APP_DIR}"

log "Done: ${APP_DIR}"
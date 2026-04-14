pipeline {
    agent any
    
    environment {
        // ID креденшиала с SSH ключом
        SSH_KEY_NAME = 'ssh-private-key'
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        
        // Пути на удаленной машине
        REMOTE_USER = 'ubuntu'
        APP_DIR = '/opt/currency-converter-bot'
    }
    
    parameters {
        // --- НОВЫЕ ПАРАМЕТРЫ ДЛЯ ОРКЕСТРАТОРА (Строки) ---
        string(name: 'IMAGE_TAG_STR', defaultValue: '', description: 'Docker image tag passed from orchestrator')
        string(name: 'VM_IP_STR', defaultValue: '', description: 'VM IP address passed from orchestrator')
        
        // --- СТАРЫЕ ПАРАМЕТРЫ ДЛЯ РУЧНОГО ЗАПУСКА (Файлы/Строки) ---
        string(name: 'OVERRIDE_IMAGE_NAME', defaultValue: '', description: 'Manual override for image name')
        file(name: 'DOCKER_IMAGE_FILE', description: 'Legacy: File containing image name')
        file(name: 'SERVER_IP_FILE', description: 'Legacy: File containing VM IP')
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '📥 Клонируем репозиторий...'
                checkout scm
            }
        }

        // ========================================================================
        // 📦 Получаем Docker Image (Умная логика приоритетов)
        // ========================================================================
        stage('Get Docker Image') {
            steps {
                script {
                    def foundImage = ""
                    
                    // Приоритет 1: Ручное переопределение (самый высокий приоритет)
                    if (params.OVERRIDE_IMAGE_NAME && params.OVERRIDE_IMAGE_NAME.trim().isNotEmpty()) {
                        foundImage = params.OVERRIDE_IMAGE_NAME.trim()
                        echo "ℹ️ [Priority 1] Используем OVERRIDE_IMAGE_NAME: ${foundImage}"
                    } 
                    // Приоритет 2: Строка от оркестратора (основной путь автоматизации)
                    else if (params.IMAGE_TAG_STR && params.IMAGE_TAG_STR.trim().isNotEmpty()) {
                        foundImage = params.IMAGE_TAG_STR.trim()
                        echo "ℹ️ [Priority 2] Используем IMAGE_TAG_STR из оркестратора: ${foundImage}"
                    } 
                    // Приоритет 3: Legacy файл (для обратной совместимости)
                    else if (params.DOCKER_IMAGE_FILE && fileExists(params.DOCKER_IMAGE_FILE)) {
                        foundImage = readFile(params.DOCKER_IMAGE_FILE).trim()
                        echo "ℹ️ [Priority 3] Используем legacy файл DOCKER_IMAGE_FILE: ${foundImage}"
                    } 
                    else {
                        error("❌ Не удалось определить Docker Image! Передайте IMAGE_TAG_STR или заполните OVERRIDE_IMAGE_NAME.")
                    }
                    
                    env.DOCKER_IMAGE = foundImage
                    echo "✅ Final Docker Image: ${env.DOCKER_IMAGE}"
                }
            }
        }

        // ========================================================================
        // 🖥️ Получаем VM IP (Умная логика приоритетов)
        // ========================================================================
        stage('Get VM IP') {
            steps {
                script {
                    def foundIp = ""
                    
                    // Приоритет 1: Строка от оркестратора
                    if (params.VM_IP_STR && params.VM_IP_STR.trim().isNotEmpty()) {
                        foundIp = params.VM_IP_STR.trim()
                        echo "ℹ️ [Priority 1] Используем VM_IP_STR из оркестратора: ${foundIp}"
                    } 
                    // Приоритет 2: Legacy файл
                    else if (params.SERVER_IP_FILE && fileExists(params.SERVER_IP_FILE)) {
                        foundIp = readFile(params.SERVER_IP_FILE).trim()
                        echo "ℹ️ [Priority 2] Используем legacy файл SERVER_IP_FILE: ${foundIp}"
                    } 
                    else {
                        error("❌ Не удалось определить VM IP! Передайте VM_IP_STR.")
                    }
                    
                    env.VM_IP = foundIp
                    printSuccess("✅ Final VM Public IP: ${env.VM_IP}")
                }
            }
        }
        
        // ========================================================================
        // 🐳 Deploy to Yandex Cloud VM via SSH
        // ========================================================================
        stage('Deploy to Yandex Cloud VM') {
            steps {
                script {
                    if (!env.VM_IP) {
                        error("❌ Переменная VM_IP не установлена.")
                    }

                    sshagent(["${SSH_KEY_NAME}"]) {
                        printLog("Подключение к ${REMOTE_USER}@${env.VM_IP}...", '🔗', 36)
                        
                        // 1. Копируем файлы конфигурации
                        printStep("Копирование docker-compose.yaml...")
                        sh """
                            scp -o StrictHostKeyChecking=no \\
                                -o UserKnownHostsFile=/dev/null \\
                                docker-compose.yaml \\
                                ${REMOTE_USER}@${env.VM_IP}:/tmp/docker-compose.yaml.tmp
                        """

                        printStep("Копирование .env...")
                        withCredentials([file(credentialsId: 'currency-bot-env-arseniy', variable: 'ENV_FILE')]) {
                            sh """
                                scp -o StrictHostKeyChecking=no \\
                                    -o UserKnownHostsFile=/dev/null \\
                                    "\${ENV_FILE}" \\
                                    ${REMOTE_USER}@${env.VM_IP}:/tmp/.env.tmp
                            """
                        }

                        printStep("Копирование vault-init.sh...")
                        withCredentials([file(credentialsId: 'vault-init-script-arseniy', variable: 'INIT_SCRIPT')]) {
                            sh """
                                scp -o StrictHostKeyChecking=no \\
                                    -o UserKnownHostsFile=/dev/null \\
                                    "\${INIT_SCRIPT}" \\
                                    ${REMOTE_USER}@${env.VM_IP}:/tmp/init-vault.sh.tmp
                            """
                        }
                        
                        // 2. Выполняем удаленные команды
                        printStep("Развертывание приложения...")
                        
                        sh """
                            ssh -o StrictHostKeyChecking=no \\
                                -o UserKnownHostsFile=/dev/null \\
                                ${REMOTE_USER}@${env.VM_IP} << 'REMOTEOF'
                                
                                set -e
                                
                                APP_DIR="${APP_DIR}"
                                DOCKER_IMAGE="${env.DOCKER_IMAGE}"
                                USER="${REMOTE_USER}"
                                
                                echo "📁 Подготовка директории \${APP_DIR}..."
                                sudo mkdir -p \${APP_DIR}/vault/scripts
                                
                                # Перемещаем файлы из /tmp в рабочую директорию
                                sudo mv /tmp/docker-compose.yaml.tmp \${APP_DIR}/docker-compose.yaml
                                sudo mv /tmp/.env.tmp \${APP_DIR}/.env
                                sudo mv /tmp/init-vault.sh.tmp \${APP_DIR}/vault/scripts/init-vault.sh
                                
                                # Выставляем права
                                sudo chown -R \${USER}:\${USER} \${APP_DIR}
                                chmod 600 \${APP_DIR}/.env
                                chmod 700 \${APP_DIR}/vault/scripts/init-vault.sh
                                
                                cd \${APP_DIR}
                                
                                echo "📥 Pulling image: \${DOCKER_IMAGE}"
                                docker pull \${DOCKER_IMAGE}
                                
                                echo "🔄 Обновление тега в docker-compose.yaml"
                                sudo sed -i "s|<image>|${env.DOCKER_IMAGE}|g" docker-compose.yaml

                                echo "🚀 Перезапуск контейнеров"
                                docker compose down || true
                                docker compose up -d
                                
                                echo "🧹 Очистка старых образов"
                                docker image prune -f
                                
                                echo "✅ Статус контейнеров:"
                                docker compose ps
                                
REMOTEOF
"""
                    }
                }
            }
        }
    }
    
    post {
        always {
            cleanWs()
        }
        failure {
            printError("Deployment failed! Check logs.")
        }
        success {
            printSuccess("Deployment successful! Image: ${env.DOCKER_IMAGE}, VM: ${env.VM_IP}")
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

def printLog(String message, String emoji = '', int colorCode = 36, boolean bold = false) {
    def boldCode = bold ? "\\e[1m" : ""
    def color = "\\e[${colorCode}m"
    def reset = "\\e[0m"
    def display = emoji ? "${emoji} ${message}" : message
    
    ansiColor('xterm') {
        sh(script: """
            set +x
            printf '${boldCode}${color}${display}${reset}\\n'
        """, returnStdout: false)
    }
}

def printSuccess(String message) { printLog(message, '✅', 32) }
def printError(String message)   { printLog(message, '❌', 31) }
def printDebug(String message)   { printLog(message, '🔍', 90) }
def printStep(String message)    { printLog(message, '📍', 35) }
pipeline {
    agent any
    
    environment {
        // ID креденшиала с SSH ключом
        SSH_KEY_NAME = 'ssh-private-key'
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        
        // Имена джоб-источников
        INFRA_ARTIFACT_JOB = 'deploy-infra'
        BUILD_ARTIFACT_JOB = 'build'
        
        // Пути на удаленной машине
        REMOTE_USER = 'ubuntu'
        APP_DIR = '/opt/currency-converter-bot'
    }
    
    parameters {
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'Docker image name (optional, если пусто - берётся из build job)')
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '📥 Клонируем репозиторий...'
                checkout scm
            }
        }

        // ========================================================================
        // 📦 Получаем Docker Image из Build Job
        // ========================================================================
        stage('Get Docker Image from Build Job') {
            steps {
                script {
                    if (params.OVERRIDE_IMAGE_NAME) {
                        env.DOCKER_IMAGE = params.OVERRIDE_IMAGE_NAME
                    } else {
                        // Проверяем, что параметр передан и не пуст
                        if (!params.DOCKER_IMAGE_FILE || params.DOCKER_IMAGE_FILE.trim().isEmpty()) {
                            error("❌ Параметр DOCKER_IMAGE_FILE не передан или пуст!")
                        }

                        if (!fileExists(params.DOCKER_IMAGE_FILE)) {
                            error("❌ Файл с образом не найден: ${params.DOCKER_IMAGE_FILE}")
                        }

                        env.DOCKER_IMAGE = readFile(params.DOCKER_IMAGE_FILE).trim()
                        if (!env.DOCKER_IMAGE) {
                            error("❌ Файл ${params.DOCKER_IMAGE_FILE} пуст!")
                        }
                    }
                }
            }
        }

        // ========================================================================
        // 🖥️ Получаем VM IP из Infra Job (Terraform Output)
        // ========================================================================
        stage('Get VM IP from Infra Job') {
            steps {
                script {
                    if (!params.SERVER_IP_FILE || params.SERVER_IP_FILE.trim().isEmpty()) {
                        error("❌ Параметр SERVER_IP_FILE не передан или пуст!")
                    }

                    if (!fileExists(params.SERVER_IP_FILE)) {
                        error("❌ Файл с IP не найден: ${params.SERVER_IP_FILE}")
                    }

                    env.VM_IP = readFile(params.SERVER_IP_FILE).trim()
                    if (!env.VM_IP) {
                        error("❌ Файл ${params.SERVER_IP_FILE} пуст!")
                    }

                    printSuccess("✅ VM Public IP: ${env.VM_IP}")
                }
            }
        }
        
        // ========================================================================
        // 🐳 Deploy to VM via SSH
        // ========================================================================
        stage('Deploy to Yandex Cloud VM') {
            steps {
                script {
                    if (!env.VM_IP) {
                        error("❌ Переменная VM_IP не установлена. Проверьте предыдущий шаг.")
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
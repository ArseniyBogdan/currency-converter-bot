pipeline {
    agent any
    
    environment {
        OS_CREDENTIALS_ID = 'rc-credentials-arseniy'
        STACK_NAME = "currency-converter-bot-infra-arseniy"
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        
        // Имя credentials в Jenkins (SSH Username with private key)
        SSH_KEY_NAME = 'arseniy_jenkins'

        INFRA_ARTIFACT_JOB = 'Bogdan/create-infra'
        BUILD_ARTIFACT_JOB = 'Bogdan/build'
    }
    
    parameters {
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'Docker image name (optional, если пусто - берётся из build job)')
    }
    
    stages {
        stage('Prepare OpenStack Env') {
            steps {
                ansiColor('xterm') {
                    printLog("Loading OpenStack credentials...", '🔐', 36)
                    loadSecretsIntoEnv("${OS_CREDENTIALS_ID}")
                    printLog("Testing OpenStack connection...", '🔑', 35)
                    sh '''
                        set +x
                        openstack token issue -f yaml
                    '''
                    printSuccess("Auth successful")
                }
            }
        }

        // ========================================================================
        // 📦 Получаем Docker Image из Build Job
        // ========================================================================
        stage('Get Docker Image from Build Job') {
            steps {
                script {
                    if (!params.IMAGE_NAME) {
                        printLog("IMAGE_NAME не указан, получаем из build job...", '📦', 36)
                
                        try {
                            copyArtifacts projectName: BUILD_ARTIFACT_JOB,
                                        filter: 'docker-image.txt',
                                        target: '.',
                                        selector: lastSuccessful(),
                                        flatten: true
                            
                            env.DOCKER_IMAGE = readFile('docker-image.txt').trim()
                            
                            printDebug("🔍 Debug: env.DOCKER_IMAGE='${env.DOCKER_IMAGE}'")
                            
                            if (!env.DOCKER_IMAGE) {
                                printError("❌ Файл docker-image.txt пустой!")
                                error("❌ Файл docker-image.txt пустой!")
                            }
                            
                            printSuccess("Docker image из build: ${env.DOCKER_IMAGE}")
                            
                        } catch (Exception e) {
                            printError("❌ Ошибка: ${e.message}")
                            throw e
                        }
                    } else {
                        env.DOCKER_IMAGE = params.IMAGE_NAME
                        printSuccess("✅ Docker image из параметра: ${env.DOCKER_IMAGE}")
                    }
                }
            }
        }

        // ========================================================================
        // 🖥️ Получаем VM IP из Infra Job
        // ========================================================================
        stage('Get VM IP from Infra Job') {
            steps {
                script {
                    printLog("Получаем IP виртуалки из артефактов infra job...", '🖥️', 36)
                    
                    copyArtifacts projectName: INFRA_ARTIFACT_JOB,
                                filter: 'stack_outputs.json',
                                target: '.',
                                selector: lastSuccessful(),
                                flatten: true
                    
                    // ✅ Читаем файл
                    def jsonContent = readFile('stack_outputs.json')
                    
                    // ✅ Парсим через Groovy readJSON
                    def outputs = readJSON text: jsonContent

                    // ✅ Отладка: показываем тип и структуру
                    echo "🔍 Parsed type: ${outputs.class}"
                    if (outputs instanceof List) {
                        echo "🔍 Это массив, элементов: ${outputs.size()}"
                    } else if (outputs instanceof Map) {
                        echo "🔍 Это объект, ключи: ${outputs.keySet()}"
                    }
                    
                    // ✅ Ищем server_private_ip (работает и для массива, и для объекта)
                    def vmIpOutput = null
                    
                    if (outputs instanceof List) {
                        vmIpOutput = outputs.find { it.output_key == 'server_private_ip' }
                    } else if (outputs instanceof Map) {
                        // Если JSON — объект с ключами как output_key
                        vmIpOutput = outputs.find { key, value -> key == 'server_private_ip' }
                    }
                    
                    // ✅ Ищем server_private_ip
                    def vmIpOutput = outputs.find { it.output_key == 'server_private_ip' }
                    
                    if (vmIpOutput) {
                        env.VM_IP = vmIpOutput.output_value.trim()
                        printSuccess("✅ VM IP: ${env.VM_IP}")
                    } else {
                        echo "⚠️ Доступные outputs:"
                        outputs.each { out ->
                            echo "  - ${out.output_key} = ${out.output_value}"
                        }
                        error("❌ Не найдено 'server_private_ip'")
                    }
                }
            }
        }
        
        // ========================================================================
        // 🔐 Проверяем доступность VM
        // ========================================================================
        stage('Check VM Accessibility') {
            steps {
                script {
                    printLog("Проверка доступности VM...", '🔍', 36)
                    sh """
                        for i in {1..5}; do
                            if ping -c 1 -W 2 ${env.VM_IP} > /dev/null 2>&1; then
                                echo "✅ VM доступен"
                                exit 0
                            fi
                            echo "⏳ Попытка \$i... VM ещё не доступен"
                            sleep 5
                        done
                        echo "⚠️ VM не ответил на ping, продолжаем..."
                    """
                }
            }
        }
        
        // ========================================================================
        // 🐳 Pull Docker Image на VM (с SSH ключом из Jenkins)
        // ========================================================================
        stage('Pull Docker Image on VM') {
            steps {
                // Используем ssh-agent для работы с SSH ключом из credentials
                sshagent(["${SSH_KEY_NAME}"]) {
                    script {
                        printLog("Pull Docker образа на VM...", '🐳', 36)
                        sh """
                            ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ubuntu@${env.VM_IP} << 'EOF'
                                cd /opt/app
                                
                                echo "📥 Pull образа: ${env.DOCKER_IMAGE}"
                                docker pull ${env.DOCKER_IMAGE}
                                
                                # Обновляем docker-compose.yaml с новым образом
                                sed -i "s|image:.*|image: ${env.DOCKER_IMAGE}|g" docker-compose.yaml
                                
                                # Перезапускаем контейнеры
                                docker-compose down
                                docker-compose up -d
                                
                                # Cleanup старых образов
                                docker image prune -f
                                
                                echo "✅ Container обновлён"
                            EOF
                        """
                    }
                }
            }
        }
        
        // ========================================================================
        // ❤️ Health Check
        // ========================================================================
        stage('Health Check') {
            steps {
                script {
                    printLog("Проверка здоровья приложения...", '❤️', 36)
                    sh """
                        for i in {1..10}; do
                            if curl -s http://${env.VM_IP}:8081/healthcheck > /dev/null 2>&1; then
                                echo "✅ Application is healthy!"
                                exit 0
                            fi
                            echo "⏳ Попытка \$i... Ждём приложение"
                            sleep 5
                        done
                        echo "❌ Health check failed!"
                        exit 1
                    """
                }
            }
        }
    }
    
    post {
        always {
            printLog("Deployment completed", '📊', 36)
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

def printStageHeader(String stageName, String emoji = '', int colorCode = 36) {
    def border = "=" * 22
    def bold = "\\e[1m"
    def color = "\\e[${colorCode}m"
    def reset = "\\e[0m"
    def display = emoji ? "${emoji} ${stageName}" : stageName

    sh(script: "set +x && echo -e '${bold}${color}${border} ${display} ${border}${reset}'", returnStdout: false)
}

def printInfo(String message)    { printLog(message, 'ℹ️', 36) }
def printSuccess(String message) { printLog(message, '✅', 32) }
def printWarning(String message) { printLog(message, '⚠️', 33) }
def printError(String message)   { printLog(message, '❌', 31) }
def printDebug(String message)   { printLog(message, '🔍', 90) }
def printStep(String message)    { printLog(message, '📍', 35) }

def loadSecretsIntoEnv(String credentialId) {
    withCredentials([string(credentialsId: credentialId, variable: 'SECRET_BLOB')]) {
        def content = SECRET_BLOB
        
        content.split(' ').each { rawLine ->
            try {
                def line = rawLine.trim()
                if (!line || line.startsWith('#')) return
                
                def parts = line.split('=', 2)
                if (parts.length != 2) return
                
                def key = parts[0].trim()
                def value = parts[1].trim()
                
                while (value.length() >= 2 && 
                      ((value.startsWith('"') && value.endsWith('"')) || 
                       (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1)
                }
                value = value.trim()
                
                env."${key}" = value
                println "✅ Loaded: ${key}"
                
            } catch (Exception e) {
                println "❌ Error loading ${key ?: 'unknown'}: ${e.message}"
            }
        }
    }
}
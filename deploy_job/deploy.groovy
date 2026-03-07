pipeline {
    agent any
    
    environment {
        OS_CREDENTIALS_ID = 'rc-credentials-arseniy'
        STACK_NAME = "currency-converter-bot-infra-arseniy"
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        
        // Имя credentials в Jenkins (SSH Username with private key)
        SSH_KEY_NAME = 'arseniy_jenkins'
        
        // Будут заполнены из артефактов
        DOCKER_IMAGE = ''
        VM_IP = ''

        INFRA_ARTIFACT_JOB = 'Bogdan/job/create-infra'
        BUILD_ARTIFACT_JOB = 'Bogdan/job/deploy'
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
                        
                        copyArtifacts projectName: BUILD_ARTIFACT_JOB,
                                     filter: 'docker-image.txt',
                                     target: '.',
                                     selector: lastSuccessful()
                        
                        env.DOCKER_IMAGE = sh(script: 'cat docker-image.txt', returnStdout: true).trim()
                        printSuccess("Docker image из build: ${env.DOCKER_IMAGE}")
                    } else {
                        env.DOCKER_IMAGE = params.IMAGE_NAME
                        printSuccess("Docker image из параметра: ${env.DOCKER_IMAGE}")
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
                                 filter: 'stack-outputs.txt',
                                 target: '.',
                                 selector: lastSuccessful()
                    
                    env.VM_IP = sh(script: '''
                        cat stack-outputs.txt | \
                        python3 -c "import sys, json; data=json.load(sys.stdin); \
                        print([o['output_value'] for o in data if o['output_key']=='server_private_ip'][0])" 2>/dev/null || echo ""
                    ''', returnStdout: true).trim()
                    
                    if (!env.VM_IP) {
                        error("❌ Не удалось получить VM_IP из артефактов!")
                    }
                    
                    printSuccess("VM IP: ${env.VM_IP}")
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
        
        content.split('\n').each { rawLine ->
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
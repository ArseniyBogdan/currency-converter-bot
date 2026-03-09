pipeline {
    agent any
    
    environment {
        OS_CREDENTIALS_ID = 'rc-credentials-arseniy'
        STACK_NAME = "currency-converter-bot-infra-arseniy"
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        
        // Имя credentials в Jenkins (SSH Username with private key)
        SSH_KEY_NAME = 'Arseniy'

        INFRA_ARTIFACT_JOB = 'Bogdan/create-infra'
        BUILD_ARTIFACT_JOB = 'Bogdan/build'
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
                    
                    // ✅ Ищем server_private_ip (работает и для массива, и для объекта)
                    def vmIpOutput = readJSON text: outputs['server_private_ip']
                    
                    if (vmIpOutput) {
                        env.VM_IP = vmIpOutput['output_value'].trim()
                        printSuccess("✅ VM IP: ${env.VM_IP}")
                    } else {
                        echo "⚠️ Доступные outputs:"
                        outputs.each { out ->
                            echo "  - ${out}"
                        }
                        error("❌ Не найдено 'server_private_ip'")
                    }
                }
            }
        }
        
        // ========================================================================
        // 🐳 Pull Docker Image на VM (с SSH ключом из Jenkins)
        // ========================================================================
        stage('Pull Docker Image on VM') {
            steps {
                sshagent(["${SSH_KEY_NAME}"]) {
                    script {
                        def VM_USER = 'ubuntu'
                        def APP_DIR = '/opt/currency-converter-bot'
                        
                        printLog("Deploying to ${VM_USER}@${env.VM_IP}...", '🐳', 36)
                        
                        printStep("Copying docker-compose.yaml...")
                        sh """
                            scp -o StrictHostKeyChecking=no \\
                                -o UserKnownHostsFile=/dev/null \\
                                docker-compose.yaml \\
                                ${VM_USER}@${env.VM_IP}:~/docker-compose.yaml.tmp
                        """

                        printStep("Copying .env from credentials...")
        
                        withCredentials([file(credentialsId: 'currency-bot-env-arseniy', variable: 'ENV_FILE')]) {
                            sh """
                                scp -o StrictHostKeyChecking=no \\
                                    -o UserKnownHostsFile=/dev/null \\
                                    "\${ENV_FILE}" \\
                                    ${VM_USER}@${env.VM_IP}:~/.env.tmp
                            """
                        }

                        printStep("Copying vault-init.sh from credentials...")
        
                        withCredentials([file(credentialsId: 'vault-init-script-arseniy', variable: 'INIT_SCRIPT')]) {
                            sh """
                                scp -o StrictHostKeyChecking=no \\
                                    -o UserKnownHostsFile=/dev/null \\
                                    "\${INIT_SCRIPT}" \\
                                    ${VM_USER}@${env.VM_IP}:~/init-vailt.sh.tmp
                            """
                        }
                        
                        printStep("Pulling image and restarting containers...")
                        
                        sh """
                            ssh -o StrictHostKeyChecking=no \\
                                -o UserKnownHostsFile=/dev/null \\
                                ${VM_USER}@${env.VM_IP} << 'REMOTEOF'
                                
                                set -e
                                APP_DIR="${APP_DIR}"
                                
                                echo "📁 Moving docker-compose.yaml to app directory..."
                                sudo mv ~/docker-compose.yaml.tmp \${APP_DIR}/docker-compose.yaml
                                sudo chown ${VM_USER}:${VM_USER} \${APP_DIR}/docker-compose.yaml

                                # ✅ Перемещаем .env файл с безопасными правами
                                sudo mv ~/.env.tmp \${APP_DIR}/.env
                                sudo chown ${VM_USER}:${VM_USER} \${APP_DIR}/.env
                                sudo chmod 600 \${APP_DIR}/.env  # 🔒 Только владелец может читать

                                # ✅ Перемещаем .env файл с безопасными правами
                                sudo mkdir \${APP_DIR}/vault/scripts
                                sudo mv ~/init-vailt.sh.tmp \${APP_DIR}/vault/scripts/init-vailt.sh
                                sudo chown ${VM_USER}:${VM_USER} \${APP_DIR}/vault/scripts/init-vailt.sh
                                sudo chmod 700 \${APP_DIR}/vault/scripts/init-vailt.sh
                                
                                cd \${APP_DIR}
                                
                                echo "📥 Pulling image: ${env.DOCKER_IMAGE}"
                                docker pull ${env.DOCKER_IMAGE}
                                
                                echo "🔄 Updating image tag in docker-compose.yaml"
                                sudo sed -i "s|<image>|${env.DOCKER_IMAGE}|g" docker-compose.yaml
                                
                                echo "🚀 Restarting containers"
                                docker compose down
                                docker compose up -d
                                
                                echo "🧹 Cleaning up old images"
                                docker image prune -f
                                
                                echo "✅ Deployment complete"
REMOTEOF
"""    
                        printSuccess("✅ Application deployed successfully")
                    }
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
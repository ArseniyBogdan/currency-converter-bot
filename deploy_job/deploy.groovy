pipeline {
    agent any
    
    environment {
        OS_CREDENTIALS_ID = 'rc-credentials-arseniy'
        STACK_NAME = "currency-converter-bot-infra-arseniy"
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        VM_IP = ''
        DOCKER_IMAGE = ''
    }

    parameters {
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'Docker image name (optional, если пусто - берётся из last successful build)')
        string(name: 'STACK_NAME', defaultValue: 'currency-converter-stack', description: 'OpenStack stack name')
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

        stage('Download Artifact from L2') {
            steps {
                // Копируем артефакт из предыдущей джобы
                copyArtifacts projectName: 'currency-converter-bot-build',
                             filter: '**/*.jar',
                             target: 'artifacts/',
                             selector: lastSuccessful()
                script {
                    env.ARTIFACT_FILE = sh(script: 'ls artifacts/*.jar', returnStdout: true).trim()
                    echo "Artifact found: ${env.ARTIFACT_FILE}"
                }
            }
        }
        
        stage('Deploy Infrastructure via Heat') {
            steps {
                script {
                    // Устанавливаем OpenStack CLI
                    sh '''
                        pip install python-openstackclient python-heatclient
                    '''
                    
                    // Аутентификация в OpenStack
                    sh '''
                        export OS_AUTH_URL=${OPENSTACK_AUTH_URL}
                        export OS_USERNAME=${OPENSTACK_USERNAME}
                        export OS_PASSWORD=${OPENSTACK_PASSWORD}
                        export OS_PROJECT_NAME=${OPENSTACK_PROJECT}
                        export OS_IDENTITY_API_VERSION=3
                        export OS_USER_DOMAIN_NAME=Default
                        export OS_PROJECT_DOMAIN_NAME=Default
                    '''
                    
                    // Проверяем是否存在 стек, если нет - создаём
                    env.STACK_EXISTS = sh(script: '''
                        openstack stack show ${STACK_NAME} --format value -c stack_status 2>/dev/null || echo "NOT_EXISTS"
                    ''', returnStdout: true).trim()
                    
                    if (env.STACK_EXISTS == "NOT_EXISTS" || env.STACK_EXISTS.contains("FAILED")) {
                        echo "Stack not exists or failed. Creating new stack..."
                        sh '''
                            openstack stack create -t heat/deploy-infra.yaml ${STACK_NAME}
                            # Ждём пока стек создастся
                            openstack stack wait ${STACK_NAME}
                        '''
                    } else {
                        echo "Stack ${STACK_NAME} already exists with status: ${STACK_EXISTS}"
                    }
                }
            }
        }
        
        stage('Get VM IP from Stack') {
            steps {
                script {
                    // Получаем IP адрес VM из стека
                    env.VM_IP = sh(script: '''
                        openstack stack output show -c output_value ${STACK_NAME} vm_ip --format value
                    ''', returnStdout: true).trim()
                    echo "VM IP: ${env.VM_IP}"
                }
            }
        }
        
        stage('Transfer Artifact to VM') {
            steps {
                script {
                    // Передаём JAR файл на VM через SCP
                    sh '''
                        scp -o StrictHostKeyChecking=no -i ${KEY_PATH} ${ARTIFACT_FILE} ubuntu@${VM_IP}:/opt/app/
                    '''
                }
            }
        }
        
        stage('Restart Application') {
            steps {
                script {
                    sh '''
                        ssh -o StrictHostKeyChecking=no -i ${KEY_PATH} ubuntu@${VM_IP} << 'EOF'
                            cd /opt/app
                            # Копируем JAR в Docker volume или обновляем образ
                            docker-compose pull
                            docker-compose up -d --force-recreate
                            docker-compose logs -f
                        EOF
                    '''
                }
            }
        }
    }
    
    post {
        always {
            echo "Deployment completed"
        }
        failure {
            echo "Deployment failed! Check logs."
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
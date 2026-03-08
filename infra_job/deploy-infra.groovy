pipeline {
    agent {
        label 'arseniy-agent' 
    }
    
    environment {
        OS_CREDENTIALS_ID = 'rc-credentials-arseniy'
        HEAT_STACK_NAME = "currency-converter-bot-infra-arseniy"
        HEAT_TEMPLATE = 'infra_job/heat/deploy-infra.yaml'

        // === Параметры Heat-шаблона ===
        OS_IMAGE_ID = 'ununtu-22.04'
        OS_FLAVOR_ID = 'm1.small'
        SSH_KEY_NAME = 'Arseniy2'
        EXISTING_SUBNET_ID = 'd80da048-c188-45a5-80e4-55d914fe58ea'
    }

    options {
        disableConcurrentBuilds()
    }
    
    stages {
        stage('Checkout') {
            steps {    
                ansiColor('xterm') {            
                    printStageHeader('Checkout', '📦', 32)
                    checkout scm
                }
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
        
        stage('Check & Cleanup Existing Infra') {
            steps {
                ansiColor('xterm') {
                    printStageHeader('Check & Cleanup', '🧹', 32)
                    script {
                        printLog("Checking if stack '${HEAT_STACK_NAME}' exists...", '🔍', 36)
                        
                        def stackExists = sh(
                            script: "openstack stack show ${HEAT_STACK_NAME} -f value -c stack_status 2>/dev/null || echo 'NOT_FOUND'",
                            returnStdout: true
                        ).trim()
                        
                        if (stackExists == 'NOT_FOUND') {
                            printSuccess("Stack '${HEAT_STACK_NAME}' does not exist. Proceeding.")
                        } else {
                            printWarning("Stack exists with status: ${stackExists}")
                            printLog("Deleting existing stack...", '🗑️', 31)
                            
                            sh '''
                                set +x
                                openstack stack delete --yes ${HEAT_STACK_NAME}
                            '''
                            printLog("Waiting for deletion...", '⏳', 35)
                            sh '''
                                while openstack stack show ${HEAT_STACK_NAME} -f value -c stack_status 2>/dev/null | grep -q .; do
                                    sleep 5
                                done
                            '''
                            printSuccess("Stack deleted")
                        }
                    }
                }
            }
        }
        
        stage('Deploy Infrastructure') {
            steps {
                ansiColor('xterm') {
                    printStageHeader('Deploy Infrastructure', '🚀', 32)

                    printLog("Creating stack: ${HEAT_STACK_NAME}", '📦', 35)
                    printLog("Template: ${HEAT_TEMPLATE}", '', 35)
                    printLog("Image: ${OS_IMAGE_ID}", '', 35)
                    printLog("Flavor: ${OS_FLAVOR_ID}", '', 35)
                    printLog("Key: ${SSH_KEY_NAME}", '', 35)
                    sh '''
                        set +x
                        openstack stack create \
                            -t ${HEAT_TEMPLATE} \
                            --parameter image_id=${OS_IMAGE_ID} \
                            --parameter flavor_id=${OS_FLAVOR_ID} \
                            --parameter key_name=${SSH_KEY_NAME} \
                            --parameter existing_subnet_id=${EXISTING_SUBNET_ID} \
                            --wait \
                            ${HEAT_STACK_NAME}
                    '''
                    printSuccess("Stack created successfully!")
                }
            }
        }
        
        stage('Collect Outputs') {
            steps {
                ansiColor('xterm') {
                    printStageHeader('Collect Outputs', '📥', 32) 
                    script {
                        printLog("Collecting stack outputs...", '📥', 36)
                        
                        // ✅ Получаем IP напрямую (для использования в текущей джобе)
                        env.SERVER_IP = sh(
                            script: "openstack stack output show -c output_value -f value ${HEAT_STACK_NAME} server_private_ip",
                            returnStdout: true
                        ).trim()

                        if (!env.SERVER_IP) {
                            error("❌ Не удалось получить server_private_ip из стека ${HEAT_STACK_NAME}")
                        }

                        printLog("Infrastructure ready at: ${env.SERVER_IP}", '🌍', 32, true)
                        
                        sh """
                            openstack stack output show --all --format json ${HEAT_STACK_NAME} > stack_outputs.json
                        """
                
                        printSuccess("Outputs saved to stack_outputs.json")
                        
                        // ✅ Архивируем артефакт
                        archiveArtifacts artifacts: 'stack_outputs.json', allowEmptyArchive: false
                    }
                }
            }
        }
    }
    
    post {
        always {
            ansiColor('xterm') {
                script {
                    printStageHeader('Post Actions', '📦', 32)
                }
                printLog("Archiving artifacts...", '🗃️', 36)
                archiveArtifacts artifacts: 'stack_outputs.txt', allowEmptyArchive: true
                printLog("Cleaning workspace...", '🧹', 36)
                cleanWs()
            }
        }
        failure {
            ansiColor('xterm') {
                script {
                    printStageHeader('❌ FAILURE', '💥', 31)
                }
                printError("DEPLOYMENT FAILED")
                printLog("Check OpenStack dashboard for details", '🔍', 31)
                sh '''
                    set +x
                    if openstack stack show ${HEAT_STACK_NAME} &>/dev/null; then
                        openstack stack delete --yes ${HEAT_STACK_NAME} || true
                    fi
                '''
            }
        }
        success {
            ansiColor('xterm') {
                script {
                    printStageHeader('🎉 SUCCESS', '✨', 32)
                }
                printSuccess("Infrastructure deployed successfully!")
                printLog("SSH: ssh ubuntu@${env.SERVER_IP}", '🔐', 36, true)
            }
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
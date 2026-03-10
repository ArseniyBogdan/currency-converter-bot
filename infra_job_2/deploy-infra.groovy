pipeline {
    agent {
        label 'arseniy-agent'  
    }
    
    environment {
        OS_CREDENTIALS_ID = 'rc-credentials-arseniy'
        TF_VAR_existing_subnet_id = 'd80da048-c188-45a5-80e4-55d914fe58ea'
        TF_VAR_ansible_ssh_private_key_file = credentials('Arseniy')
        
        STACK_NAME = "currency-converter-bot-infra-arseniy"
        TF_STATE_FILE = "terraform.tfstate"
        ANSIBLE_INVENTORY = "ansible/inventory/hosts.ini"
    }
    
    stages {
        stage('Checkout') {
            steps {
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
        
        stage('Setup') {
            steps {
                sh '''
                    echo "🔧 Setting up tools..."
                    
                    # Install prerequisites
                    sudo apt-get update -qq
                    sudo apt-get install -y -qq wget unzip curl
                    
                    # Install Terraform
                    if ! command -v terraform &> /dev/null; then
                        echo "📦 Installing Terraform..."
                        wget -q https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
                        unzip -o terraform_1.6.0_linux_amd64.zip
                        sudo mv terraform /usr/local/bin/
                        rm -f terraform_1.6.0_linux_amd64.zip
                        echo "✅ Terraform installed: $(terraform --version)"
                    else
                        echo "✅ Terraform already installed: $(terraform --version)"
                    fi
                    
                    # Install Ansible
                    if ! command -v ansible &> /dev/null; then
                        echo "📦 Installing Ansible..."
                        sudo apt-get install -y -qq ansible
                        echo "✅ Ansible installed: $(ansible --version | head -1)"
                    else
                        echo "✅ Ansible already installed: $(ansible --version | head -1)"
                    fi
                    
                    # Install Ansible Docker collection
                    echo "📦 Installing Ansible Docker collection..."
                    ansible-galaxy collection install community.docker --force
                    echo "✅ Setup complete!"
                '''
            }
        }
        
        stage('Terraform Init') {
            steps {
                dir('infra_job/terraform') {
                    sh '''
                        terraform init -input=false
                    '''
                }
            }
        }
        
        stage('Terraform Plan') {
            steps {
                dir('infra_job/terraform') {
                    sh '''
                        terraform plan -out=tfplan -input=false -var="stack_name=${STACK_NAME}"
                    '''
                }
            }
        }
        
        stage('Terraform Apply') {
            steps {
                dir('infra_job/terraform') {
                    sh '''
                        terraform apply -input=false tfplan
                    '''
                }
            }
        }
        
        stage('Get Infrastructure Outputs') {
            steps {
                script {
                    dir('infra_job/terraform') {
                        env.SERVER_IP = sh(
                            script: 'terraform output -raw server_private_ip',
                            returnStdout: true
                        ).trim()
                        
                        env.SERVER_NAME = sh(
                            script: 'terraform output -raw server_name',
                            returnStdout: true
                        ).trim()
                    }
                    
                    echo "🖥️ Server IP: ${env.SERVER_IP}"
                    echo " Server Name: ${env.SERVER_NAME}"
                    
                    // Generate Ansible inventory
                    writeFile file: "${env.ANSIBLE_INVENTORY}", text: """
[bot]
${env.SERVER_NAME} ansible_host=${env.SERVER_IP} ansible_user=ubuntu ansible_ssh_private_key_file=${env.TF_VAR_ansible_ssh_private_key_file}

[bot:vars]
ansible_python_interpreter=/usr/bin/python3
ansible_ssh_common_args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null'
"""
                }
            }
        }
        
        stage('Wait for VM Ready') {
            steps {
                script {
                    echo "⏳ Waiting for VM to be ready..."
                    def maxAttempts = 30
                    def attempt = 0
                    
                    while (attempt < maxAttempts) {
                        attempt++
                        try {
                            sh """
                                ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no ubuntu@${env.SERVER_IP} 'echo "VM is ready"'
                            """
                            echo "✅ VM is ready!"
                            break
                        } catch (Exception e) {
                            echo "⏳ Attempt ${attempt}/${maxAttempts} - VM not ready yet..."
                            sleep 10
                        }
                    }
                    
                    if (attempt >= maxAttempts) {
                        error("❌ VM did not become ready in time")
                    }
                }
            }
        }
        
        stage('Ansible Provision') {
            when {
                expression { return params.ACTION == 'deploy' || params.ACTION == 'provision' }
            }
            steps {
                dir('infra_job/ansible') {
                    sh '''
                        ansible-playbook -i inventory/hosts.ini playbooks/provision.yml \
                            --extra-vars "ansible_ssh_private_key_file=${TF_VAR_ansible_ssh_private_key_file}"
                    '''
                }
            }
        }
    }
    
    post {
        always {
            echo "📊 Deployment completed"
            
            archiveArtifacts artifacts: 'infra_job/terraform/*.tfstate', allowEmptyArchive: true
            cleanWs()
            
        }
        
        success {
            echo "✅ Deployment successful!"
            echo "🖥️ Server: ${env.SERVER_NAME} (${env.SERVER_IP})"
            echo " Application: http://${env.SERVER_IP}:8081"
            echo "🔐 Vault: http://${env.SERVER_IP}:8200"
        }
        
        failure {
            echo "❌ Deployment failed! Check logs."
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
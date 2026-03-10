pipeline {
    agent {
        label 'arseniy-agent'  
    }
    
    environment {
        TF_VAR_os_auth_url = credentials('OS_AUTH_URL')
        TF_VAR_os_username = credentials('OS_USERNAME')
        TF_VAR_os_password = credentials('OS_PASSWORD')
        TF_VAR_os_project_name = credentials('OS_PROJECT_NAME')
        TF_VAR_existing_subnet_id = 'd80da048-c188-45a5-80e4-55d914fe58ea'
        TF_VAR_ansible_ssh_private_key_file = credentials('SSH_KEY_PATH')
        
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
        
        stage('Setup') {
            steps {
                sh '''
                    # Install Terraform
                    if ! command -v terraform &> /dev/null; then
                        wget -q https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
                        unzip terraform_1.6.0_linux_amd64.zip
                        sudo mv terraform /usr/local/bin/
                    fi
                    
                    # Install Ansible
                    if ! command -v ansible &> /dev/null; then
                        sudo apt update
                        sudo apt install -y ansible
                    fi
                    
                    # Install Ansible Docker collection
                    ansible-galaxy collection install community.docker
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
            
            node {
                archiveArtifacts artifacts: 'infra_job/terraform/*.tfstate', allowEmptyArchive: true
                cleanWs()
            }
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
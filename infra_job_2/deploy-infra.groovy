pipeline {
    agent any

    // Привязка секретов из Jenkins Credentials
    environment {
        
        YC_TOKEN              = credentials('yc-token')
        TF_FOLDER_ID          = "b1gm94s1sde2ispi5k21"
        TF_SUBNET_ID          = "fl80id702e4irnblcd63"
        TF_SSH_PUB_KEY        = credentials('ssh-public-key')
        
        // Отключаем проверку SSH-ключей для новых ВМ
        ANSIBLE_HOST_KEY_CHECKING = 'False'
    }

    stages {
        stage('1. Подготовка окружения') {
            steps {
                // Убедимся, что временные файлы Ansible не будут блокироваться правами
                sh 'chmod 700 ~/.ssh 2>/dev/null || true'
            }
        }

        stage('2. Terraform: Init & Plan') {
            steps {
                sh 'terraform init -input=false -no-color'
                sh '''
                    terraform plan \
                      -var="folder_id=${TF_FOLDER_ID}" \
                      -var="subnet_id=${TF_SUBNET_ID}" \
                      -var="ssh_public_key=${TF_SSH_PUB_KEY}" \
                      -input=false -out=tfplan -no-color
                '''
            }
        }

        stage('3. Terraform: Apply') {
            steps {
                sh 'terraform apply -auto-approve tfplan -no-color'
            }
        }

        stage('Обновление Inventory') {
            steps {
                script {
                    // Извлекаем публичный IP из вывода Terraform
                    def serverIp = sh(
                        script: 'terraform output -raw instance_ip', 
                        returnStdout: true
                    ).trim()
                    env.SERVER_IP = serverIp
                    echo "🌍 Выделен IP: ${serverIp}"

                    // Заменяем плейсхолдер VM_IP в inventory.yml
                    // Используем | как разделитель sed, чтобы избежать конфликтов с точками в IP
                    sh "sed -i 's|VM_IP|${serverIp}|g' inventory.yml"
                    echo "✅ inventory.yml обновлён"
                    
                    // Показываем актуальный инвентарь для отладки
                    sh 'cat inventory.yml'
                }
            }
        }

        stage('Ansible: Provision') {
            steps {
                // Безопасная работа с приватным SSH-ключом
                withCredentials([sshUserPrivateKey(
                    credentialsId: 'ssh-private-key', 
                    keyFileVariable: 'SSH_KEY_PATH',
                    passphraseVariable: ''
                )]) {
                    sh """
                        ansible-playbook -i inventory.yml playbook.yml \\
                          --private-key \${SSH_KEY_PATH} \\
                          -vv
                    """
                }
            }
        }
    }

    post {
        always {
            // Очистка временных файлов
            sh 'rm -f main.tf tfplan 2>/dev/null || true'
            
            // Архивация логов Terraform и Ansible
            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true
        }
        success {
            echo "🎉 Инфраструктура успешно развернута и настроена!"
            echo "🔗 SSH: ssh -i ~/.ssh/id_rsa ubuntu@${env.SERVER_IP}"
        }
        failure {
            echo "❌ Пайплайн завершился с ошибкой. Проверьте логи выше."
            // Можно добавить шаг отправки уведомления в Slack/Telegram
        }
        cleanup {
            // Опционально: terraform destroy в случае критического сбоя на ранних этапах
            // sh 'terraform destroy -auto-approve -input=false'
        }
    }
}
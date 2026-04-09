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

        stage('Terraform: Init & Plan') {
            steps {
                dir('infra_job_2'){
                    sh '''
                        terraform init -input=false -no-color
                    '''
                    sh '''
                        terraform plan \
                        -var="folder_id=${TF_FOLDER_ID}" \
                        -var="subnet_id=${TF_SUBNET_ID}" \
                        -var="ssh_public_key=${TF_SSH_PUB_KEY}" \
                        -input=false -out=tfplan -no-color
                    '''
                }
            }
        }

        stage('Terraform: Apply') {
            steps {
                dir('infra_job_2'){
                    sh 'terraform apply -auto-approve tfplan -no-color'
                }
            }
        }

        stage('Обновление Inventory') {
            steps {
                script {
                    dir('infra_job_2'){
                        // 1. Извлекаем публичный IP из вывода Terraform
                        // Используем try-catch на случай, если output еще не создан или пуст
                        def serverIp = ""
                        try {
                            serverIp = sh(
                                script: 'terraform output -raw server_public_ip', 
                                returnStdout: true
                            ).trim()
                        } catch (Exception e) {
                            error("❌ Не удалось получить server_public_ip из Terraform. Проверьте outputs.tf")
                        }

                        if (!serverIp) {
                            error("❌ server_public_ip пуст!")
                        }

                        env.SERVER_IP = serverIp
                        echo "🌍 Выделен Public IP: ${serverIp}"

                        // 2. Сохраняем IP в файл для артефактов
                        // Этот файл будет доступен другим джобам через copyArtifacts
                        writeFile file: 'server_public_ip.txt', text: serverIp
                        echo "💾 IP сохранен в server_public_ip.txt"

                        // 3. Обновляем Ansible Inventory
                        // Заменяем плейсхолдер VM_IP в inventory.yml
                        sh "sed -i 's|VM_IP|${serverIp}|g' ansible/inventory.yml"
                        echo "✅ inventory.yml обновлён"
                        
                        // Показываем актуальный инвентарь для отладки
                        sh 'cat ansible/inventory.yml'
                    }
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
                    dir('infra_job_2/ansible'){
                        sh """
                            ansible-playbook -i inventory.yml playbook.yml \\
                            --private-key \${SSH_KEY_PATH} \\
                            -vv
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            // Очистка временных файлов
            dir('infra_job_2'){
                sh 'rm -f main.tf tfplan 2>/dev/null || true'

                // Архивация логов Terraform и Ansible
                archiveArtifacts artifacts: '**/*.log, server_public_ip.txt', allowEmptyArchive: true
            }
        }
        success {
            echo "🎉 Инфраструктура успешно развернута и настроена!"
            echo "🔗 SSH: ssh -i ~/.ssh/id_rsa ubuntu@${env.SERVER_IP}"
        }
        failure {
            echo "❌ Пайплайн завершился с ошибкой. Проверьте логи выше."
        }
    }
}
pipeline {
    agent any

    environment {
        KUBE_CONFIG_ID = 'minikube-kubeconfig' // ID креденшиала с kubeconfig
        K8S_DIR = 'k8s' // Папка с манифестами
    }

    parameters {
        string(name: 'DOCKER_IMAGE', defaultValue: '', description: 'Full Docker Image Name (e.g., docker.io/user/app:tag)')
        booleanParam(name: 'CLEAN_INSTALL', defaultValue: false, description: 'Удалить всё перед установкой?')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare K8s Manifests') {
            steps {
                script {
                    if (!params.DOCKER_IMAGE) {
                        error("❌ DOCKER_IMAGE параметр обязателен!")
                    }
                    
                    echo "🔧 Подготовка манифестов..."
                    // Заменяем плейсхолдер в deployment.yaml на реальный образ
                    sh """
                        sed -i 's|<IMAGE_PLACEHOLDER>|${params.DOCKER_IMAGE}|g' ${K8S_DIR}/deployments.yaml
                    """
                    
                    echo "✅ Манифесты готовы для образа: ${params.DOCKER_IMAGE}"
                }
            }
        }

        stage('Deploy to Minikube') {
            steps {
                withCredentials([file(credentialsId: KUBE_CONFIG_ID, variable: 'KUBECONFIG')]) {
                    script {
                        if (params.CLEAN_INSTALL) {
                            echo "🧹 Очистка предыдущих ресурсов..."
                            sh """
                                kubectl delete -f ${K8S_DIR}/ --ignore-not-found=true
                                sleep 10
                            """
                        }

                        echo "🚀 Применение конфигурации Kubernetes..."
                        
                        // 1. Сервисы (чтобы DNS имена стали доступны)
                        sh "kubectl apply -f ${K8S_DIR}/services.yaml"
                        
                        // 2. Деплойменты (Vault, Mongo, RabbitMQ, App)
                        sh "kubectl apply -f ${K8S_DIR}/deployments.yaml"

                        echo "⏳ Ожидание готовности подов..."
                        // Ждем, пока все деплойменты будут готовы
                        sh """
                            kubectl rollout status deployment/vault --timeout=300s
                            kubectl rollout status deployment/mongo --timeout=300s
                            kubectl rollout status deployment/rabbitmq --timeout=300s
                            kubectl rollout status deployment/currency-converter-bot --timeout=300s
                        """
                    }
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                withCredentials([file(credentialsId: KUBE_CONFIG_ID, variable: 'KUBECONFIG')]) {
                    sh """
                        echo "📊 Статус подов:"
                        kubectl get pods
                        
                        echo "🌐 Сервисы:"
                        kubectl get services
                        
                        # Получаем внешний IP для проверки
                        APP_URL=\$(minikube service app-service --url | head -n 1)
                        echo "🔗 Приложение доступно по адресу: \${APP_URL}"
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "🎉 Деплой в Kubernetes завершен успешно!"
        }
        failure {
            echo "❌ Ошибка деплоя. Проверьте логи kubectl."
        }
    }
}
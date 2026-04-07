pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'arseniybogdan/currency-converter-bot'
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo '📥 Клонируем репозиторий...'
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                echo '🔨 Собираем проект через Gradle...'
                script {
                    if (fileExists('gradlew')) {
                        echo '✅ Найден Gradle Wrapper'
                        sh '''
                            chmod +x ./gradlew
                            
                            ./gradlew clean fatJar -x test
                        '''
                    } else {
                        echo '⚠️ Gradle Wrapper не найден, используем системный gradle'
                        sh 'gradle clean build -x test'
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                echo '🐳 Сборка Docker-образа...'
                script {
                    // Формируем полное имя образа
                    env.DOCKER_IMAGE_LOCAL = "${DOCKER_REGISTRY}/${DOCKER_REPO}:local"

                    sh """
                        docker build \
                            -t ${env.DOCKER_IMAGE_LOCAL} \
                            -f Dockerfile \
                            .
                    """
                }
            }
        }

        stage('Docker Push') {
            steps {
                echo '🚀 Публикация образа в Docker Hub...'
                script {
                    // Формируем тег из номера билда если не задан
                    if (!env.IMAGE_TAG) {
                        env.IMAGE_TAG = "build-${BUILD_NUMBER}"
                    }
                    
                    // Формируем полное имя образа
                    env.DOCKER_IMAGE = "${DOCKER_REGISTRY}/${DOCKER_REPO}:${IMAGE_TAG}"
                    env.DOCKER_IMAGE_LATEST = "${DOCKER_REGISTRY}/${DOCKER_REPO}:latest"
                }
                withCredentials([usernamePassword(
                    credentialsId: 'DockerHubArseniy',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        # Авторизация в Docker Hub
                        echo "\${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY} -u "\${DOCKER_USER}" --password-stdin
                        
                        # Tag образа с версией и latest
                        docker tag ${env.DOCKER_IMAGE_LOCAL} ${env.DOCKER_IMAGE}
                        docker tag ${env.DOCKER_IMAGE_LOCAL} ${env.DOCKER_IMAGE_LATEST}
                        
                        # Push обоих тегов
                        docker push ${env.DOCKER_IMAGE}
                        docker push ${env.DOCKER_IMAGE_LATEST}
                        
                        # Сохраняем имя образа в файл для deploy джобы
                        echo "${env.DOCKER_IMAGE}" > docker-image.txt
                        
                        # Выход из реестра
                        docker logout ${DOCKER_REGISTRY}
                    """
                }
                // Архивируем файл с именем образа для передачи в deploy
                archiveArtifacts artifacts: 'docker-image.txt', allowEmptyArchive: true
            }
        }
    }

    post {
        always {
            echo '🧹 Очистка...'
            sh "docker rmi ${DOCKER_IMAGE}:${IMAGE_TAG} ${DOCKER_IMAGE}:latest 2>/dev/null || true"
            cleanWs()
        }
        failure {
            echo '❌ Сборка провалилась! Проверьте логи выше.'
        }
        success {
            echo "🎉 Сборка успешна! Образ: ${DOCKER_IMAGE}:${IMAGE_TAG}"
            echo "🔗 Docker Hub: https://hub.docker.com/r/${DOCKER_IMAGE}/tags"
        }
    }
}
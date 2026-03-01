pipeline {
    agent {
        label 'shklyarova-node' 
    }

    environment {
        JAVA_HOME = '/opt/jdk/jdk-23.0.2+7' 
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
                    // Проверяем, есть ли gradlew в корне
                    if (fileExists('gradlew')) {
                        echo 'Найден Gradle Wrapper, используем его...'
                        sh '''
                            chmod +x ./gradlew
                            ./gradlew clean build -x test
                        '''
                        // Флаг -x test пропускает тесты. Уберите его, если хотите запускать тесты.
                    } else {
                        echo 'Gradle Wrapper не найден, используем системный gradle...'
                        // Убедитесь, что gradle установлен на агенте и добавлен в PATH
                        sh 'gradle clean build -x test'
                    }
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                echo '📦 Ищем и архивируем JAR файл...'
                // Для Gradle артефакты обычно лежат в build/libs/
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true, allowEmptyArchive: false
            }
        }
    }

    post {
        always {
            echo '✅ Этап завершен.'
            cleanWs()
        }
        failure {
            echo '❌ Сборка провалилась! Проверьте логи выше.'
        }
        success {
            echo '🎉 Сборка успешна! Артефакт сохранен.'
        }
    }
}

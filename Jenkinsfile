pipeline {
    agent {
        // Указываем метку вашего агента, который мы настраивали
        label 'arseniy-agent' 
    }

    environment {
        // Переменные окружения
        MAVEN_HOME = '/usr/bin/mvn' // Путь к mvn на агенте, проверьте командой 'which mvn'
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64' // Путь к Java, проверьте 'which java'
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
                echo '🔨 Собираем проект через Maven...'
                ls -la
                // Сборка и пропуск тестов (если тестов нет или они падают без токена)
                // Если нужны тесты, уберите -DskipTests
                sh '''
                    chmod +x mvnw 2>/dev/null || true
                    ./mvnw clean package -DskipTests || mvn clean package -DskipTests
                '''
            }
        }

        stage('Archive Artifact') {
            steps {
                echo '📦 Архивируем JAR файл...'
                // Ищем собранный jar файл в целевой папке
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: false
            }
        }
    }

    post {
        always {
            // Очищаем рабочую директорию после сборки (опционально)
            // cleanWs() 
            echo '✅ Сборка завершена.'
        }
        failure {
            echo '❌ Сборка провалилась! Проверьте логи.'
        }
    }
}

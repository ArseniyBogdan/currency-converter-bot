pipeline {
    agent any

    environment {
        // Имена джоб
        BUILD_JOB_NAME      = 'build'
        INFRA_JOB_NAME      = 'deploy-infra'
        DEPLOY_JOB_NAME     = 'deploy'
        
        // Имена файлов артефактов (должны совпадать с теми, что архивируют дочерние джобы)
        ARTIFACT_IMAGE_NAME = 'docker-image.txt'
        ARTIFACT_IP_NAME    = 'server_public_ip.txt'
    }

    parameters {
        booleanParam(name: 'RUN_BUILD', defaultValue: true, description: 'Запустить сборку?')
        booleanParam(name: 'RUN_INFRA', defaultValue: true, description: 'Развернуть инфраструктуру?')
        booleanParam(name: 'RUN_DEPLOY', defaultValue: true, description: 'Выполнить деплой?')
        string(name: 'CUSTOM_IMAGE_TAG', defaultValue: '', description: 'Переопределить тег образа (опционально)')
    }

    stages {
        // ========================================================================
        // STAGE 1: BUILD
        // ========================================================================
        stage('1. Build Application') {
            when { expression { params.RUN_BUILD == true } }
            steps {
                echo "🚀 Запуск сборки: ${BUILD_JOB_NAME}"
                build job: BUILD_JOB_NAME, propagate: true, wait: true
                
                // ⬇️ СКАЧИВАЕМ АРТЕФАКТ ПОСЛЕ ЗАВЕРШЕНИЯ ДЖОБЫ
                script {
                    echo "⬇️ Скачивание артефакта образа..."
                    copyArtifacts(
                        projectName: BUILD_JOB_NAME,
                        filter: ARTIFACT_IMAGE_NAME,
                        target: 'build_artifacts', // Сохраняем в отдельную папку, чтобы не затереть
                        selector: lastSuccessful(),
                        flatten: true
                    )
                    // Сохраняем путь к файлу в глобальную переменную
                    env.LOCAL_IMAGE_FILE_PATH = "${WORKSPACE}/build_artifacts/${ARTIFACT_IMAGE_NAME}"
                    
                    if (!fileExists(env.LOCAL_IMAGE_FILE_PATH)) {
                        error("❌ Артефакт ${ARTIFACT_IMAGE_NAME} не найден после сборки!")
                    }
                    echo "✅ Образ сохранен локально: ${env.LOCAL_IMAGE_FILE_PATH}"
                }
            }
        }

        // ========================================================================
        // STAGE 2: INFRASTRUCTURE
        // ========================================================================
        stage('2. Provision Infrastructure') {
            when { expression { params.RUN_INFRA == true } }
            steps {
                echo "🏗️ Запуск инфраструктуры: ${INFRA_JOB_NAME}"
                build job: INFRA_JOB_NAME, propagate: true, wait: true
                
                // ⬇️ СКАЧИВАЕМ АРТЕФАКТ ПОСЛЕ ЗАВЕРШЕНИЯ ДЖОБЫ
                script {
                    echo "⬇️ Скачивание артефакта IP..."
                    copyArtifacts(
                        projectName: INFRA_JOB_NAME,
                        filter: ARTIFACT_IP_NAME,
                        target: 'infra_artifacts',
                        selector: lastSuccessful(),
                        flatten: true
                    )
                    
                    env.LOCAL_IP_FILE_PATH = "${WORKSPACE}/infra_artifacts/${ARTIFACT_IP_NAME}"
                    
                    if (!fileExists(env.LOCAL_IP_FILE_PATH)) {
                        error("❌ Артефакт ${ARTIFACT_IP_NAME} не найден после создания инфраструктуры!")
                    }
                    echo "✅ IP сохранен локально: ${env.LOCAL_IP_FILE_PATH}"
                }
            }
        }

        // ========================================================================
        // STAGE 3: DEPLOY
        // ========================================================================
        stage('3. Deploy Application') {
            when { expression { params.RUN_DEPLOY == true } }
            steps {
                script {
                    echo "📦 Запуск деплоя: ${DEPLOY_JOB_NAME}"

                    def deployParams = []

                    build job: DEPLOY_JOB_NAME, 
                        propagate: true, 
                        wait: true,
                        parameters: deployParams
                        
                    echo "✅ Деплой инициирован успешно."
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "🎉 Полный цикл завершен!"
        }
        failure {
            echo "❌ Ошибка на одном из этапов."
        }
    }
}
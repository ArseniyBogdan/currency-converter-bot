plugins {
    id("java")
    id("application")
    id("io.spring.dependency-management") version "1.1.4"
    id("org.asciidoctor.jvm.convert") version "3.3.2"
}

group = "ru.spbstu.hsai"
version = "0.0"
val jarBaseName = "currency-converter-bot"
application.mainClass.set("ru.spbstu.hsai.Application")

repositories {
    mavenCentral()
}


dependencies {
    implementation(platform("org.springframework:spring-framework-bom:6.2.5"))
    implementation(platform("org.springframework.security:spring-security-bom:6.2.5"))
    implementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))

    // Spring Framework
    implementation("org.springframework:spring-webflux")
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-core")
    implementation("org.springframework.data:spring-data-jpa:3.4.4")
    implementation("org.mongodb:mongodb-jdbc:2.2.3")
    implementation("org.springframework.modulith:spring-modulith:1.3.5")
    implementation("org.springframework.modulith:spring-modulith-docs:1.3.5")
    implementation("org.springframework.amqp:spring-rabbit:3.2.4")
    implementation("org.springframework.vault:spring-vault-core:3.1.2")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-web")

    // Netty
    implementation("io.projectreactor.netty:reactor-netty-http:1.2.4")

    // Jakarta
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0-M2")
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0-M2")

    // Telegram
    implementation("org.telegram:telegrambots:6.9.7.1")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")

    // MongoDB
    implementation("org.springframework.data:spring-data-mongodb:4.4.4")
    implementation("org.mongodb:mongodb-driver-reactivestreams:5.4.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.springframework.restdocs:spring-restdocs-webtestclient:3.0.3")
    testImplementation("org.springframework.restdocs:spring-restdocs-asciidoctor:3.0.3")
    testImplementation("io.projectreactor:reactor-test:3.7.5")
    testImplementation("org.springframework:spring-test")
    implementation("org.springframework.modulith:spring-modulith-test:1.3.4")

    // For calculating currencies
    implementation("net.objecthunter:exp4j:0.4.8")

    //for csv
    implementation("com.opencsv:opencsv:5.11")

    // Документация
    implementation("org.springdoc:springdoc-openapi-webflux-ui:1.8.0")
    implementation("org.springdoc:springdoc-openapi-webflux-core:1.8.0")
    implementation("org.springdoc:springdoc-openapi-common:1.8.0")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.30")
}


tasks.withType<Jar> {
    archiveBaseName.set(jarBaseName)
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.distZip {
    isEnabled = false
}
tasks.distZip {
    isEnabled = false
}

tasks.register("fatJar", Jar::class) {
    archiveBaseName.set(jarBaseName)

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get()
        .map {
            if (it.isDirectory) it else zipTree(it).matching {
                // Исключаем файлы цифровых подписей
                exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/Log4j2Plugins.dat")
            }
        })
    with(tasks.jar.get() as CopySpec)
}

tasks.register("generateDocs", org.asciidoctor.gradle.jvm.AsciidoctorTask::class) {
    sourceDir("build/docs")

    attributes(mapOf(
        "source-highlighter" to "coderay",
        "icons" to "font",
        "toc" to "left",
        "setanchors" to "true")
    )
}

tasks.test {
    useJUnitPlatform()
}
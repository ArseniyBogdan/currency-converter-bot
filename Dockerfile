FROM openjdk:24
WORKDIR /app
# The application's jar file
ARG JAR_FILE=./build/libs/currency-converter-bot-0.0.jar
# Add the application's jar to the container
ADD ${JAR_FILE} /app.jar

# HTTP port
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app.jar"]
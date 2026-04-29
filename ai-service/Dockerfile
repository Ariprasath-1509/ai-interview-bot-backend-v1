FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./ai-service/target/ai-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6003
ENTRYPOINT ["java", "-jar", "app.jar"]

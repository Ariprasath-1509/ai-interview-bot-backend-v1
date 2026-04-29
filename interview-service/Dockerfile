FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./interview-service/target/interview-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6006
ENTRYPOINT ["java", "-jar", "app.jar"]

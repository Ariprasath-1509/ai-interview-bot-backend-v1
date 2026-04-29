FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./auth-service/target/auth-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6004
ENTRYPOINT ["java", "-jar", "app.jar"]

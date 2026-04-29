FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./observer-service/target/observer-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6007
ENTRYPOINT ["java", "-jar", "app.jar"]

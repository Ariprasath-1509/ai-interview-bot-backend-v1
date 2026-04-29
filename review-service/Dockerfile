FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./review-service/target/review-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6008
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./compliance-service/target/compliance-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6005
ENTRYPOINT ["java", "-jar", "app.jar"]

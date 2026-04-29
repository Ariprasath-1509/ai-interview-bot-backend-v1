FROM openjdk:21-jdk-slim
WORKDIR /app
COPY ./api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6002
ENTRYPOINT ["java", "-jar", "app.jar"]

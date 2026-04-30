FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY --chown=185 ./ai-service/target/ai-service-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 6003
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]

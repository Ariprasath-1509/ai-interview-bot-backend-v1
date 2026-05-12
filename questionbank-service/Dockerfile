FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY --chown=185 ./target/questionbank-api-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 6010
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]
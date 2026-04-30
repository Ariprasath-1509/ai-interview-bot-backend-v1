FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY --chown=185 ./auth-service/target/auth-service-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 6004
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]

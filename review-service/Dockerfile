FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY --chown=185 ./review-service/target/review-service-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 6008
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]

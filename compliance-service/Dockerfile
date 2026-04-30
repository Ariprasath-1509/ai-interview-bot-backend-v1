FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY --chown=185 ./compliance-service/target/compliance-service-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 6005
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]

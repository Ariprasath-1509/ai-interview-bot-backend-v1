FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY --chown=185 ./api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 6002
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]

FROM registry.access.redhat.com/ubi9/openjdk-21:1.20

COPY --chown=185 api-gateway/target/*.jar /deployments/app.jar

EXPOSE 6002

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/deployments/app.jar"]
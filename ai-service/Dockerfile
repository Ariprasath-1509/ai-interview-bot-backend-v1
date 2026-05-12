FROM registry.access.redhat.com/ubi9/openjdk-21:1.20

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 6003

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
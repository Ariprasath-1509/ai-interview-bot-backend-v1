FROM registry.access.redhat.com/ubi9/openjdk-21:1.20

RUN microdnf install -y curl && microdnf clean all

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 6006

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
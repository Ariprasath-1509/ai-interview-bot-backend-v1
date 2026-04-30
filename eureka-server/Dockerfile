FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
COPY ./eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 6009
ENTRYPOINT ["java", "-jar", "app.jar"]

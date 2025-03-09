FROM gradle:8.10.1-jdk21 AS build
ARG BUILD_HOME=/app
WORKDIR $BUILD_HOME
COPY . /app/.


FROM eclipse-temurin:21-jre-alpine



COPY $BUILD_HOME/build/libs/*.jar /app/manager-0.0.1-SNAPSHOT.jar

CMD ["java", "-jar", "/app/manager-0.0.1-SNAPSHOT.jar"]
FROM gradle:latest as cache
ENV APP_HOME=/usr/app/
RUN mkdir $APP_HOME
ENV GRADLE_USER_HOME /usr/gradle/cache

RUN mkdir -p /usr/gradle/cache
COPY build.gradle $APP_HOME
WORKDIR $APP_HOME
RUN ./gradlew build || return 0

FROM gradle:latest AS build
ENV APP_HOME=/usr/app/
COPY --from=cache /usr/gradle/cache /home/gradle/.gradle
WORKDIR $APP_HOME
COPY . .
RUN gradle bootJar

FROM eclipse-temurin:21-jdk-alpine
ENV APP_HOME=/usr/app
WORKDIR $APP_HOME
COPY --from=build $APP_HOME/build/libs/manager-0.0.1-SNAPSHOT.jar manager.jar

HEALTHCHECK --interval=5s --timeout=3s --start-period=30s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "manager.jar"]
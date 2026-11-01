FROM gradle:8.9-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# Gli orari delle partite sono in ora italiana: senza questo la JVM userebbe UTC
# e le scadenze (-24h, -12h, inizio) scatterebbero con un'ora o due di ritardo.
ENV TZ=Europe/Rome
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# BuildKit cache mount: /root/.m2 persists between builds on the same host
# so Maven never re-downloads the internet on every deploy.
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/uploads
COPY --from=build /app/target/uaeitjobs-0.0.1-SNAPSHOT.jar app.jar

# Install Chromium and all OS-level libraries it needs to run headlessly.
# Spring Boot fat JARs use nested JARs (BOOT-INF/lib/) that the standard -cp flag
# cannot read.  PropertiesLauncher is Spring Boot's own classloader — it unpacks
# and loads nested JARs correctly, so the Playwright CLI class is reachable.
# -Dloader.main sets the class to run; remaining args are forwarded to its main().
RUN java -Dloader.main=com.microsoft.playwright.CLI \
         -cp app.jar \
         org.springframework.boot.loader.launch.PropertiesLauncher \
         install --with-deps chromium

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

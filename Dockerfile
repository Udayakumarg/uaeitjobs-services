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
# The playwright CLI is bundled inside the fat jar — no separate Node install needed.
# --with-deps installs both the browser binary and every system library in one step.
RUN java -cp app.jar com.microsoft.playwright.CLI install --with-deps chromium

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

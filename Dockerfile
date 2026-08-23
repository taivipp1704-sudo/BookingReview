FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:22-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system claritycam \
    && useradd --system --gid claritycam --home-dir /app --shell /usr/sbin/nologin claritycam \
    && mkdir -p /app /data/identity \
    && chown -R claritycam:claritycam /app /data/identity

WORKDIR /app
COPY --from=build --chown=claritycam:claritycam /workspace/target/claritycam-platform-api-0.1.0.jar /app/app.jar

ENV IDENTITY_STORAGE=/data/identity
USER claritycam
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

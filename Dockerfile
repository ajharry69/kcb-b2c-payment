FROM eclipse-temurin:24-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean build --exclude-task test

FROM eclipse-temurin:24-jre-alpine
LABEL org.opencontainers.image.source="https://github.com/ajharry69/kcb-b2c-payment"
LABEL org.opencontainers.image.licenses="Apache-2.0"

RUN addgroup -g 1000 kyosk \
        && adduser -u 1000 -G kyosk -s /bin/sh -D kyosk
# use of numeric UID:GID is recommended for k8s use for the following reasons:
#   1. consistency across environments ensuring file permissions are applied correctly.
#   2. user friendly names may resolve to different numeric IDs across systems, hence
#      leading to unintended access/permissions.
USER 1000:1000
WORKDIR /app
COPY --chown=1000:1000 --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
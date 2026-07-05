FROM gradle:9.2.1-jdk21 AS build

ARG APP_MODULE
WORKDIR /workspace

COPY settings.gradle build.gradle ./
COPY shared ./shared
COPY ssp-app ./ssp-app
COPY dsp-app ./dsp-app

RUN gradle --no-daemon ":${APP_MODULE}:installDist"

FROM eclipse-temurin:21-jre

ARG APP_MODULE
WORKDIR /app

COPY --from=build "/workspace/${APP_MODULE}/build/install/${APP_MODULE}" /app
RUN ln -s "/app/bin/${APP_MODULE}" /app/bin/run-app

EXPOSE 8080 8081

ENTRYPOINT ["/app/bin/run-app"]

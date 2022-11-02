# syntax=docker/dockerfile:1
FROM gradle:7.4-jdk17-alpine AS build
ENV APP_HOME=/SmokeManagerTgBot/
WORKDIR $APP_HOME
COPY --chown=gradle:gradle . .
RUN gradle build


FROM openjdk:17-alpine
ENV ARTIFACT_NAME=SmokeManagerTgBot-1.0-SNAPSHOT.jar
ENV APP_HOME=/SmokeManagerTgBot/
WORKDIR $APP_HOME
COPY --from=build $APP_HOME/build/libs/$ARTIFACT_NAME .

ENTRYPOINT exec java -jar ${ARTIFACT_NAME}
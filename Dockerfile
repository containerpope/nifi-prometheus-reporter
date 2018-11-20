FROM maven:3.5.3-jdk-8-slim

COPY ./nifi-prometheus-nar /app/nifi-prometheus-nar
COPY ./nifi-prometheus-reporting-task /app/nifi-prometheus-reporting-task
COPY pom.xml /app
WORKDIR /app

RUN ls -l
RUN mvn clean install

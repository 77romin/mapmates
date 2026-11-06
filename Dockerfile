FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY back/pom.xml back/pom.xml
RUN mvn -B -f back/pom.xml dependency:go-offline
COPY back back
COPY front front
RUN mvn -B -f back/pom.xml verify
FROM tomcat:10.1-jdk17-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/back/target/mapmates.war /usr/local/tomcat/webapps/mapmates.war
EXPOSE 8080

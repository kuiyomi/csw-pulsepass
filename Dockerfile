FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY libs libs
COPY services services
RUN mvn --batch-mode -q -pl services/${MODULE} -am -DskipTests package && \
    if [ "${MODULE}" = inventory-service ]; then cp services/${MODULE}/target/*-exec.jar /app.jar; \
    else cp services/${MODULE}/target/*.jar /app.jar; fi
FROM eclipse-temurin:21-jre
COPY --from=build /app.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]

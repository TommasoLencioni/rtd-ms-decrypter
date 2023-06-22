FROM maven:3.9.0-amazoncorretto-17@sha256:0d683f66624265935e836c9d2c3851ce3cf250cb48c9929d979d8d80f62d8590 as buildtime

WORKDIR /build
COPY . .

RUN mvn clean package

FROM amazoncorretto:17.0.6-al2023@sha256:cc085f1712ea61335c347eabc2f11980737529ac5ef6d0c34104b31fd48697ab as runtime

VOLUME /tmp
WORKDIR /app

COPY --from=buildtime /build/target/*.jar /app/app.jar
# The agent is enabled at runtime via JAVA_TOOL_OPTIONS.
ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.4.11/applicationinsights-agent-3.4.11.jar /app/applicationinsights-agent.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM openjdk:13.0.1

ARG JAR_FILE=build/libs/c*.jar

COPY ${JAR_FILE} OCBConnector.jar

# Expose HTTP port
EXPOSE 8080
ENTRYPOINT ["java","-jar","/OCBConnector.jar"]
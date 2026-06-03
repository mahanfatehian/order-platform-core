
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre

VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
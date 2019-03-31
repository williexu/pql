FROM openjdk:8-jre-alpine
COPY target/pqlserver-*-SNAPSHOT-standalone.jar /pqlserver.jar
CMD [ "/usr/bin/java", "-jar", "/pqlserver.jar" ]

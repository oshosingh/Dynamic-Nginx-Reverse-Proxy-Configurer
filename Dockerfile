FROM maven as MAVEN_BUILD
WORKDIR /app/be
COPY ./ ./
RUN mvn clean install -DskipTests

FROM amazoncorretto:11-alpine-jdk
WORKDIR /app/be
COPY --from=MAVEN_BUILD /app/be/target/*.jar ./app.jar
CMD ["java", "-jar", "app.jar"]
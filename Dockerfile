FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/boomslime-bot-1.0-SNAPSHOT.jar .

CMD ["java", "-jar", "boomslime-bot-1.0-SNAPSHOT.jar"]
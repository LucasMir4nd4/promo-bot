# ─── Stage 1: Build ───────────────────────────────────────────
FROM maven:3.9.4-eclipse-temurin-17-alpine AS build

WORKDIR /app
COPY pom.xml .
# Baixa dependências em camada separada (cache do Docker)
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Cria usuário não-root por segurança
RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

COPY --from=build /app/target/amazon-promo-bot-1.0.0.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

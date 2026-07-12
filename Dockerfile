# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# baixa as dependências antes de copiar o código-fonte,
# aproveitando cache de camada do Docker quando só o código muda
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# usuário não-root, boa prática de segurança em produção
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
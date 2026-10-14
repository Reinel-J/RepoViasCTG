# --- Etapa 1: build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos primero el pom.xml para aprovechar el cache de capas de Docker:
# si solo cambia el código, no se vuelven a descargar las dependencias.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# --- Etapa 2: ejecución ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# La API corre con un usuario sin privilegios. La carpeta de fotos se crea con ese dueño para que un volumen
# nuevo la herede (si ya tenías el volumen uploads_data de una versión anterior, ver el README).
RUN addgroup -S viactg && adduser -S -G viactg -u 10001 viactg \
    && mkdir -p /app/uploads && chown viactg:viactg /app/uploads

COPY --from=build /app/target/*.jar app.jar

USER viactg
EXPOSE 8080
# Usa hasta el 75 % de la memoria del contenedor para el heap (por defecto Java toma solo el 25 %).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

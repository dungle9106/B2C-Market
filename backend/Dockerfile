# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/backend-*.jar app.jar
# uploaded images live here: mount a volume (docker-compose) or use a persistent disk (Render)
RUN mkdir -p /app/uploads
# the container listens on 8080 (docker-compose, nginx); Render overrides PORT
ENV PORT=8080 \
    UPLOAD_DIR=/app/uploads \
    JAVA_OPTS="-Xmx512m -XX:+UseSerialGC"
EXPOSE 8080
# Render / Railway / Fly.io give the port in $PORT
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dspring.jpa.show-sql=false -jar app.jar"]

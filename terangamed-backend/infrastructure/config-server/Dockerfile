# ─────────────────────────────────────────────────────────────────────────────
# TerangaMed — Config Server (port 8888)
# Build depuis 'terangamed-backend/' :
#   docker build -f infrastructure/config-server/Dockerfile -t terangamed/config-server:1.0.0 .
# ─────────────────────────────────────────────────────────────────────────────

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

ENV MAVEN_OPTS="-Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8"

# Copie de TOUS les pom.xml du reactor (validation Maven multi-module)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/pom.xml
COPY infrastructure/config-server/pom.xml infrastructure/config-server/pom.xml
COPY infrastructure/discovery-server/pom.xml infrastructure/discovery-server/pom.xml
COPY infrastructure/api-gateway/pom.xml infrastructure/api-gateway/pom.xml
COPY services/patient-service/pom.xml services/patient-service/pom.xml
COPY services/doctor-service/pom.xml services/doctor-service/pom.xml
COPY services/appointment-service/pom.xml services/appointment-service/pom.xml
COPY services/medical-record-service/pom.xml services/medical-record-service/pom.xml
COPY services/notification-service/pom.xml services/notification-service/pom.xml

RUN mvn -B -e -pl infrastructure/config-server -am dependency:go-offline -Dmaven.test.skip=true || true

COPY common-lib/src common-lib/src
COPY infrastructure/config-server/src infrastructure/config-server/src

RUN mvn -B -e -pl infrastructure/config-server -am clean package -Dmaven.test.skip=true -Pfast

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S terangamed && adduser -S terangamed -G terangamed
WORKDIR /app
COPY --from=builder /workspace/infrastructure/config-server/target/*.jar app.jar
RUN chown terangamed:terangamed /app/app.jar
USER terangamed

EXPOSE 8888

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -q --spider http://localhost:8888/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

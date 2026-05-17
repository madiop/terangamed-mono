# ─────────────────────────────────────────────────────────────────────────────
# TerangaMed — doctor-service (port 8082)
# Build depuis 'terangamed-backend/' :
#   docker build -f services/doctor-service/Dockerfile -t terangamed/doctor-service:1.0.0 .
# ─────────────────────────────────────────────────────────────────────────────

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

ENV MAVEN_OPTS="-Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8"

# Copie du POM parent + TOUS les pom.xml des modules listés dans <modules> :
# Maven valide la structure complète du reactor avant d'appliquer -pl.
# Si un module est listé dans le parent mais son pom.xml absent, le build échoue.
# On copie donc tous les pom.xml (très légers) — seules les SOURCES utiles seront
# copiées plus loin (common-lib + doctor-service).
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

# Pré-télécharge les dépendances de doctor-service (et ses dépendances —am)
RUN mvn -B -e -pl services/doctor-service -am dependency:go-offline -Dmaven.test.skip=true || true

# Sources : uniquement common-lib + doctor-service (les seuls modules qu'on veut compiler)
COPY common-lib/src common-lib/src
COPY services/doctor-service/src services/doctor-service/src

RUN mvn -B -e -pl services/doctor-service -am clean package -Dmaven.test.skip=true -Pfast

# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S terangamed && adduser -S terangamed -G terangamed
WORKDIR /app
COPY --from=builder /workspace/services/doctor-service/target/*.jar app.jar
RUN chown terangamed:terangamed /app/app.jar
USER terangamed

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=10 \
    CMD wget -q --spider http://localhost:8082/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

# ─────────────────────────────────────────────────────────────────────────────
# TerangaMed — medical-record-service (port 8084)
# Build depuis 'terangamed-backend/' :
#   docker build -f services/medical-record-service/Dockerfile -t terangamed/medical-record-service:1.0.0 .
# ─────────────────────────────────────────────────────────────────────────────

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

ENV MAVEN_OPTS="-Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8"

# Copie du POM parent + TOUS les pom.xml des modules listés dans <modules> :
# Maven valide la structure complète du reactor avant d'appliquer -pl.
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

# Pré-télécharge les dépendances (échec OK si problème transitoire)
RUN mvn -B -e -pl services/medical-record-service -am dependency:go-offline -Dmaven.test.skip=true || true

# Sources : common-lib + medical-record-service uniquement
COPY common-lib/src common-lib/src
COPY services/medical-record-service/src services/medical-record-service/src

RUN mvn -B -e -pl services/medical-record-service -am clean package -Dmaven.test.skip=true -Pfast

# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Police DejaVu Sans (Regular + Bold) requise par OpenHTMLtoPDF pour rendre du
# texte dans les PDFs d'ordonnances. Sans cette font, le renderer plante avec
# `NullPointerException: PDFont.willBeSubset()`. apk packaging plutôt qu'un TTF
# en classpath → plus déterministe, pas de download au build Maven, et l'OS
# gère le cycle de vie de la font (update, sécurité).
# Le paquet ttf-dejavu place les fichiers dans /usr/share/fonts/dejavu/.
RUN apk add --no-cache ttf-dejavu

RUN addgroup -S terangamed && adduser -S terangamed -G terangamed
WORKDIR /app
COPY --from=builder /workspace/services/medical-record-service/target/*.jar app.jar
RUN chown terangamed:terangamed /app/app.jar
USER terangamed

EXPOSE 8084

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=10 \
    CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

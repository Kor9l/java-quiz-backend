FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

# A JDK rather than a JRE, and not by accident: the Java practice track compiles
# submissions at runtime through ToolProvider.getSystemJavaCompiler(), which is null without
# the jdk.compiler module. The check below is what turns a wrong base image into a failed
# build instead of a practice track that 500s on every task.
FROM eclipse-temurin:17-jdk-alpine
RUN java --list-modules | grep -q '^jdk.compiler@' \
    || { echo "This image has no jdk.compiler; the practice sandbox cannot compile."; exit 1; }
WORKDIR /app
# Quarkus fast-jar: four directories rather than one uber-jar. Copied biggest-and-stablest
# first, so a code-only change only rebuilds the last two layers.
COPY --from=build /src/target/quarkus-app/lib/ lib/
COPY --from=build /src/target/quarkus-app/quarkus/ quarkus/
COPY --from=build /src/target/quarkus-app/app/ app/
COPY --from=build /src/target/quarkus-app/quarkus-run.jar quarkus-run.jar

# An AppCDS archive, dumped by the JVM that will run the application, from the directory it
# will run in. Both of those matter: the archive records its classpath and its JVM build, and
# a mismatch on either makes the JVM discard it — silently, at the cost of the whole gain.
#
# The training run needs no database. Flyway is told not to retry, the boot dies reaching the
# datasource, and everything loaded up to that point is archived — which on the free tier's
# 0.1 CPU is worth ~8 s of the boot.
RUN java -XX:ArchiveClassesAtExit=/app/app-cds.jsa \
      -Dquarkus.flyway.connect-retries=0 \
      -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:1/none \
      -Dquarkus.datasource.jdbc.login-timeout=1S \
      -jar /app/quarkus-run.jar > /tmp/cds-dump.log 2>&1 || true; \
    test -f /app/app-cds.jsa || { echo "AppCDS: no archive was written"; cat /tmp/cds-dump.log; exit 1; }

# -Xshare:on turns "archive unusable" from a silent fallback into a hard failure, so the build
# breaks here rather than the deploy quietly losing the gain. It is deliberately NOT in the
# ENTRYPOINT: there, a base-image patch bump would take the service down instead of just
# costing a slower boot.
RUN java -Xshare:on -XX:SharedArchiveFile=/app/app-cds.jsa \
      -Dquarkus.flyway.connect-retries=0 \
      -Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:1/none \
      -jar /app/quarkus-run.jar > /tmp/cds-check.log 2>&1 || true; \
    if grep -q "shared archive" /tmp/cds-check.log; then \
      echo "AppCDS archive rejected by the runtime JVM:"; grep -A2 "shared archive" /tmp/cds-check.log; exit 1; \
    fi; rm -f /tmp/cds-dump.log /tmp/cds-check.log

EXPOSE 8080
# Free hosting tiers give the container 512 MB and 0.1 CPU. Two things follow.
#
# -XX:TieredStopAtLevel=1 keeps the JIT at C1: on a tenth of a core the C2 compiler threads
# compete with the boot they are supposed to be optimising, and measured at that quota this
# single flag takes the boot from ~50 s to ~29 s. It costs nothing in steady state here —
# median login (BCrypt cost 10, the heaviest CPU path) is unchanged inside noise, because a
# service that sleeps every 15 minutes never reaches the throughput where C2 pays for itself.
#
# 65% rather than 75% of 512 MB leaves room for metaspace, code cache, thread stacks and the
# CDS mapping alongside a measured ~131 MiB resident.
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=65.0", \
            "-XX:TieredStopAtLevel=1", \
            "-XX:SharedArchiveFile=/app/app-cds.jsa", \
            "-jar", "/app/quarkus-run.jar"]

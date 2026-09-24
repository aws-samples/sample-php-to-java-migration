# Multi-stage build for the Symfony Demo Java/Spring Boot migration.
#
# Stage 1 builds the runnable Spring Boot JAR with Maven; stage 2 runs it on a
# slim JRE. Used both for local runs and by the parity test environment
# (src/test/resources/parity/compose-parity.yml) as the "java" target.
#
# Base images are pulled from Amazon ECR Public (public.ecr.aws — the ECR mirror
# of the Docker Hub official images). ECR Public allows anonymous pulls with no
# registry authentication and avoids Docker Hub rate limits, and many enterprise
# security policies require container images to come from an ECR-hosted source.
# The references are literal (not ARG-parameterized) so static security scanners
# can resolve them.

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first for faster incremental builds.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Build the application (tests run in CI / via the harness, not in the image build).
COPY src ./src
RUN mvn -B package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/symfony-demo-*.jar app.jar

# The parity harness expects the Java app on 8080.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

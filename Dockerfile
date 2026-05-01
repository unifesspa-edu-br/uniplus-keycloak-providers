FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

COPY pom.xml ./
COPY cpf-matcher/pom.xml cpf-matcher/pom.xml
COPY cpf-matcher/src cpf-matcher/src

RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.5.7 AS runtime

ARG VERSION=1.0.0

LABEL org.opencontainers.image.source="https://github.com/unifesspa-edu-br/uniplus-keycloak-providers" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.title="Uni+ Keycloak" \
      org.opencontainers.image.version="${VERSION}"

COPY --from=builder /workspace/cpf-matcher/target/cpf-matcher-*.jar /opt/keycloak/providers/

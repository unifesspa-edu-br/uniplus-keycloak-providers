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

# Default CMD para evitar restart loop quando o operador esquece o `command:`
# no compose/manifesto. Sem `--optimized` para manter a imagem agnóstica de
# build-time options (KC_DB, KC_HEALTH_ENABLED, KC_FEATURES) — testes de
# integração usam H2/dev-mem, HML e PROD usam Postgres. O preço é ~20s a
# mais no primeiro boot enquanto o `kc.sh start` faz a augmentation com a
# config efetiva do container; reboots subsequentes reaproveitam o cache.
# Override em DEV: `command: ["start-dev", "--import-realm"]`.
CMD ["start"]

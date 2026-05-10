# Uni+ Keycloak Providers

Providers customizados em Java SPI para o **Keycloak** institucional da plataforma **Uni+** (Universidade Federal do Sul e Sudeste do Pará — Unifesspa). Authenticators, mappers e event listeners que estendem o comportamento padrão do Keycloak para atender requisitos específicos da Unifesspa, em especial Identity Brokering com **gov.br Login Único** e User Federation com **LDAP institucional**.

## Providers neste repositório

| Módulo | Tipo | Descrição |
|---|---|---|
| `cpf-matcher` | Authenticator (`first broker login`) | Matching tolerante por CPF entre identidades gov.br e users LDAP-federados, com fallback para LDAP malformado (10 dígitos sem zero à esquerda) e auto-heal de atributos. Ver [`cpf-matcher/README.md`](./cpf-matcher/README.md). |

## Stack

- **Java:** 21 LTS
- **Build:** Maven 3.9+
- **Keycloak alvo:** 26.6.1

## Build

```bash
mvn clean package
```

Cada módulo gera seu JAR em `<modulo>/target/<modulo>-<versao>.jar`.

## Deploy

Os JARs são distribuídos como providers do Keycloak — colocados em `/opt/keycloak/providers/` (ou diretório equivalente conforme o deploy).

Em ambiente de **dev local** (uniplus-api), o `docker-compose.yml` faz volume mount do JAR compilado.

Em **homologação/produção**, o JAR é incluído na imagem Docker do Keycloak institucional via Helm chart (Story de operação separada, fora do escopo deste repo).

## Como consumir a imagem

A imagem oficial de consumo dos providers Uni+ é publicada no GHCR:

```bash
docker pull ghcr.io/unifesspa-edu-br/uniplus-keycloak:26.6.1-0
```

### Tag scheme

`ghcr.io/unifesspa-edu-br/uniplus-keycloak:<KC-VERSION>-<PATCH>` onde:

- `<KC-VERSION>` = versão exata do Keycloak base (ex.: `26.6.1`)
- `<PATCH>` = revisão dos providers Uni+ sobre essa base (`-0`, `-1`, `-2`, …)

A cada release, três tags Docker são publicadas:

| Tag | Aponta para | Uso |
|---|---|---|
| `:26.6.1-0` | release imutável | pinning estrito (PROD, HML) |
| `:26.6.1` | último patch dos providers Uni+ sobre KC `26.6.1` | soft-pin dentro da mesma KC version |
| `:latest` | última release publicada | dev/exploração |

> **Migração do scheme legado `:1.x`** — descontinuado a partir de `26.6.1-0`. Tags `:1.0.x` continuam pulláveis no GHCR mas não recebem atualizações. Migrar pinning para `:<KC>-<PATCH>`.

### Por ambiente

Para **DEV**, substitua a imagem base do Keycloak no `docker-compose.yml` do `uniplus-api`:

```yaml
image: ghcr.io/unifesspa-edu-br/uniplus-keycloak:26.6.1-0
```

O caminho legacy de desenvolvimento local continua suportado para quem trabalha no SPI: compilar o JAR com Maven e montar o arquivo gerado em `/opt/keycloak/providers/`.

Para **HML/PRD/standalone**, o Helm chart deve apontar para a mesma imagem versionada:

```text
ghcr.io/unifesspa-edu-br/uniplus-keycloak:26.6.1-0
```

A imagem precisa ficar pública após o primeiro push, porque o repositório é público e os ambientes não devem depender de autenticação para pull. No GitHub, confira em **Packages > uniplus-keycloak > Package settings > Danger Zone > Change visibility** e ajuste para **Public** se necessário.

## Como fazer release

1. Ajustar o `pom.xml` (raiz e `cpf-matcher/pom.xml`), o `Dockerfile` (`ARG VERSION`) e o `<keycloak.version>` em `pom.xml` para a próxima versão alinhada ao Keycloak alvo (`<KC>-<PATCH>`).
2. Executar `mvn clean package` e validar os testes.
3. Fazer commit e push da alteração.
4. Criar e enviar a tag:

```bash
git tag v26.6.1-0
git push origin v26.6.1-0
```

O workflow `.github/workflows/release.yml` dispara automaticamente para tags `v*.*.*`, valida o formato `v<KC>-<PATCH>` e — se válido — publica o JAR e o checksum SHA-256 no GitHub Release e envia a imagem Docker para o GHCR com as tags `26.6.1-0`, `26.6.1` e `latest`. Tags fora do formato (ex.: `v26.6.1` sem `-<PATCH>`) abortam o workflow para evitar colisão entre release imutável e soft-pin.

Após a release, faça um novo commit bumpando os 3 arquivos de versão (`pom.xml`, `cpf-matcher/pom.xml`, `Dockerfile` `ARG VERSION`) para a próxima `-SNAPSHOT` esperada — ex.: `26.6.1-1-SNAPSHOT` se planejar nova patch dos providers sobre o mesmo KC 26.6.1, ou `26.6.2-0-SNAPSHOT` se for acompanhar bump do Keycloak. Isso evita que `mvn package` local regenere artefato com versão idêntica à release publicada.

## Verificação pós-release

Após publicar `v26.6.1-0`, valide:

```bash
curl -L -o cpf-matcher-26.6.1-0.jar https://github.com/unifesspa-edu-br/uniplus-keycloak-providers/releases/download/v26.6.1-0/cpf-matcher-26.6.1-0.jar
docker pull ghcr.io/unifesspa-edu-br/uniplus-keycloak:26.6.1-0
docker run --rm ghcr.io/unifesspa-edu-br/uniplus-keycloak:26.6.1-0 show-config
```

Ao iniciar o Keycloak em ambiente de teste, confirme nos logs que o provider `cpf-matcher` foi carregado.

## Estrutura

```
uniplus-keycloak-providers/
├── pom.xml                    Parent pom (multi-module)
├── cpf-matcher/               Primeiro provider (Authenticator)
│   ├── pom.xml
│   ├── src/main/java/...
│   ├── src/main/resources/META-INF/services/...
│   ├── src/test/java/...
│   └── README.md
└── (futuros providers como siblings)
```

Novos providers entram como módulos siblings de `cpf-matcher`. O parent pom centraliza:

- Versão do Keycloak alvo
- Versão do Java
- Plugins Maven comuns (compiler, surefire)
- Dependências `provided` do Keycloak (server-spi, server-spi-private, services)

## Padrões do projeto

- **Idioma:** documentação e mensagens user-facing em **pt-BR**. Identificadores Java em inglês (convenção da linguagem).
- **Conventional commits** em pt-BR — `feat(cpf-matcher): adicionar fallback para LDAP malformado`
- **Branch naming:** `feature/{issue-number}-{slug}`, `fix/{issue-number}-{slug}`
- **Sem issue, sem código:** toda implementação rastreada em GitHub Issues (sub-issues da Feature pai em [`uniplus-api#8`](https://github.com/unifesspa-edu-br/uniplus-api/issues/8))
- **Testes:** JUnit 5 + Mockito; cobertura de cenários explícitos em `cpf-matcher`.

Detalhes em [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Repositórios relacionados

| Repositório | Papel |
|---|---|
| [`uniplus-api`](https://github.com/unifesspa-edu-br/uniplus-api) | Backend .NET 10. Hospeda `docker-compose.yml` que monta os JARs deste repo no Keycloak local. |
| [`uniplus-web`](https://github.com/unifesspa-edu-br/uniplus-web) | Frontend Angular 21 (Nx workspace). |
| [`uniplus-docs`](https://github.com/unifesspa-edu-br/uniplus-docs) | ADRs, especificações, documentação técnica. ADR-029 documenta a estratégia de Identity Brokering. |

## Licença

Apache 2.0 — ver [`LICENSE`](./LICENSE).

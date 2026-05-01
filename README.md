# Uni+ Keycloak Providers

Providers customizados em Java SPI para o **Keycloak** institucional da plataforma **Uni+** (Universidade Federal do Sul e Sudeste do Pará — Unifesspa). Authenticators, mappers e event listeners que estendem o comportamento padrão do Keycloak para atender requisitos específicos da Unifesspa, em especial Identity Brokering com **gov.br Login Único** e User Federation com **LDAP institucional**.

## Providers neste repositório

| Módulo | Tipo | Descrição |
|---|---|---|
| `cpf-matcher` | Authenticator (`first broker login`) | Matching tolerante por CPF entre identidades gov.br e users LDAP-federados, com fallback para LDAP malformado (10 dígitos sem zero à esquerda) e auto-heal de atributos. Ver [`cpf-matcher/README.md`](./cpf-matcher/README.md). |

## Stack

- **Java:** 21 LTS
- **Build:** Maven 3.9+
- **Keycloak alvo:** 26.5.7

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
docker pull ghcr.io/unifesspa-edu-br/uniplus-keycloak:1.0.0
```

Para **DEV**, substitua a imagem base do Keycloak no `docker-compose.yml` do `uniplus-api`:

```yaml
image: ghcr.io/unifesspa-edu-br/uniplus-keycloak:1.0.0
```

Essa substituição entra no lugar de `quay.io/keycloak/keycloak:26.5`. O caminho legacy de desenvolvimento local continua suportado para quem trabalha no SPI: compilar o JAR com Maven e montar o arquivo gerado em `/opt/keycloak/providers/`.

Para **HML/PRD**, o Helm chart deve apontar para a mesma imagem versionada:

```text
ghcr.io/unifesspa-edu-br/uniplus-keycloak:1.0.0
```

A imagem precisa ficar pública após o primeiro push, porque o repositório é público e DEV/HML/PRD não devem depender de autenticação para pull. No GitHub, confira em **Packages > uniplus-keycloak > Package settings > Danger Zone > Change visibility** e ajuste para **Public** se necessário.

## Como fazer release

1. Ajustar o `pom.xml` para a próxima versão sem `-SNAPSHOT`.
2. Executar `mvn clean package` e validar os testes.
3. Fazer commit e push da alteração.
4. Criar e enviar a tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

O workflow `.github/workflows/release.yml` dispara automaticamente para tags `v*.*.*`, publica o JAR e o checksum SHA-256 no GitHub Release e envia a imagem Docker para o GHCR com as tags `1.0.0`, `1.0` e `latest`.

Após a release, crie um novo commit bumpando o projeto para a próxima versão `-SNAPSHOT`.

## Verificação pós-release

Após publicar `v1.0.0`, valide:

```bash
curl -L -o cpf-matcher-1.0.0.jar https://github.com/unifesspa-edu-br/uniplus-keycloak-providers/releases/download/v1.0.0/cpf-matcher-1.0.0.jar
docker pull ghcr.io/unifesspa-edu-br/uniplus-keycloak:1.0.0
docker run --rm ghcr.io/unifesspa-edu-br/uniplus-keycloak:1.0.0 show-config
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

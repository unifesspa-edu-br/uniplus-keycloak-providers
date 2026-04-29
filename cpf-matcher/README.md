# cpf-matcher

Authenticator SPI para Keycloak 26.5 que faz matching tolerante por **CPF** entre identidades federadas (gov.br) e users existentes no realm — incluindo o caso do LDAP institucional Unifesspa com `brPersonCPF` truncado em 10 dígitos.

## Por que existe

O matching padrão do Keycloak (`idp-detect-existing-broker-user`) casa por **email**. Para a Unifesspa, isso é problemático:

1. Servidores frequentemente cadastram conta gov.br com email pessoal (Gmail/Hotmail), não institucional → criam contas duplicadas no realm.
2. O LDAP institucional tem bug histórico: parte dos `brPersonCPF` está armazenado com 10 dígitos (zero à esquerda truncado por carga inicial via `int` em algum sistema legado). gov.br retorna sempre 11 dígitos no claim `sub` → matching ingênuo por CPF falha para esses entries.

Este Authenticator resolve os dois problemas no mesmo lugar.

## Como funciona

Durante o flow `first broker login`, ao receber um broker context do gov.br:

```
sub do gov.br (sempre 11 dig)
        │
        ▼
┌─ tenta matching: searchForUserByUserAttribute("cpf", sub) ─┐
│                                                              │
│   ENCONTRA?  ──── sim ──→ registra EXISTING_USER_INFO        │
│       │                   (sem auto-heal — já está canônico)│
│       │                                                      │
│       └── não, e sub começa com "0":                         │
│             tenta searchByUserAttribute("cpf", sub.substring(1))
│                                                              │
│   ENCONTRA?  ──── sim ──→ aplica auto-heal:                  │
│                              user.setSingleAttribute("cpf",  │
│                                                       sub)  │
│                            registra EXISTING_USER_INFO       │
│       │                                                      │
│       └── não → context.attempted() (sem registrar nada)     │
└──────────────────────────────────────────────────────────────┘
```

Em **todos** os caminhos (encontrou ou não), o Authenticator chama `context.attempted()`. Quando registra `EXISTING_USER_INFO`, o próximo executor do flow (`Automatically Set Existing User` ou similar) usa esse note para fazer o link efetivo. Quando não registra, o flow segue para criação de user novo.

## Como buildar

```bash
mvn clean package
```

Gera `cpf-matcher/target/cpf-matcher-1.0.0-SNAPSHOT.jar`.

## Como deployar (dev local)

O `docker-compose.yml` do **uniplus-api** monta o JAR em `/opt/keycloak/providers/`:

```yaml
keycloak:
  volumes:
    - ../uniplus-keycloak-providers/cpf-matcher/target/cpf-matcher-1.0.0-SNAPSHOT.jar:/opt/keycloak/providers/cpf-matcher.jar
  command: ["start-dev", "--import-realm"]
```

Após `mvn package`, derrubar e subir o Keycloak:

```bash
docker compose -f docker/docker-compose.yml restart keycloak
```

Logs do Keycloak ao subir devem mostrar:

```
INFO  [...] Detected deployments: [..., cpf-matcher.jar]
```

## Como configurar o flow no realm

Manualmente (console admin) ou via script setup (`uniplus-api/scripts/setup-keycloak-dev.sh`):

1. Criar uma cópia do flow built-in `first broker login` chamada `first broker login com cpf`.
2. Inserir execution **`Uni+ — Detect Existing Broker User by CPF`** (o nosso Authenticator) **antes** do execution `Confirm Link Existing Account`.
3. Definir requirement como `ALTERNATIVE` (mesmo nível do detect-existing-broker-user padrão), para que o flow tente nosso matcher primeiro e caia no padrão se não encontrar.
4. Em **Identity Providers → govbr → Settings → First Login Flow**, selecionar `first broker login com cpf`.

## Testes

```bash
mvn test
```

Testes unitários cobrem:

- Cenário 1 — match direto com CPF de 11 dígitos
- Cenário 2 — fallback para 10 dígitos com auto-heal
- Cenário 3 — sem match (delega ao próximo executor)
- Edge cases: CPF nulo, em branco, tamanho inválido (10/12 dig vindo do broker), CPF não começando com `0` (não tenta fallback)
- Verificação que auto-heal escreve o valor **canônico** (11 dig com zero à esquerda)

## Logs em produção

Nível **info** registra qual tentativa de matching funcionou:

```
CPF matcher: user existente encontrado por CPF canônico (11 dig) — username='lara.almeida'
CPF matcher: user existente encontrado via fallback LDAP malformado (10 dig) — username='kevin.peixoto'. Aplicando auto-heal do atributo cpf para formato canônico.
```

Nível **debug** registra os casos em que delega ao próximo executor.

## Limitações conhecidas

- O auto-heal escreve no atributo `cpf` do user no Keycloak, mas em users LDAP-federados read-only o **LDAP institucional permanece com o valor truncado original**. Correção definitiva exige migração one-time no LDAP (issue separada com DIRSI).
- Suporta apenas o caso "10 dígitos sem zero à esquerda". Outros formatos malformados (com pontuação, com espaços, etc.) não são tratados — são responsabilidade da camada que escreve no LDAP.
- Não valida DV do CPF — assume que o gov.br só retorna CPFs válidos.

## Referências

- [Issue #1](https://github.com/unifesspa-edu-br/uniplus-keycloak-providers/issues/1) — Story que originou este módulo
- [uniplus-docs ADR-029](https://github.com/unifesspa-edu-br/uniplus-docs/pull/108) — Identity Brokering com gov.br
- Documentação Keycloak SPI: https://www.keycloak.org/docs/latest/server_development/index.html#_auth_spi

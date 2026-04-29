# Guia de contribuição

Este documento descreve como contribuir com `uniplus-keycloak-providers`. Leia antes de abrir sua primeira issue ou PR.

## Pré-requisitos

| Ferramenta | Versão mínima |
|---|---|
| JDK | 21 LTS |
| Maven | 3.9+ |
| Git | 2.40+ |
| GitHub CLI (`gh`) | 2.50+ |

## Workflow obrigatório

### Regra de ouro: sem issue, sem código

- **Nunca** implementar código sem issue/story vinculada no GitHub.
- **Nunca** trabalhar diretamente em `main` — sempre feature branch.
- Toda Story aqui deve ser sub-issue de uma Feature em [`uniplus-api`](https://github.com/unifesspa-edu-br/uniplus-api) (cross-repo via sub-issues GA 2024-10).

### Fluxo

```
1. Issue (story, feat, fix, chore) com critérios de aceite
2. Feature branch: git checkout -b feature/{issue-number}-{slug}
3. Implementar (código + testes)
4. mvn clean verify (testes verdes obrigatório)
5. Commit em conventional commits (pt-BR)
6. Push + abrir PR vinculando "Closes #N"
7. Review + merge
```

## Branch naming

- `feature/{issue-number}-{slug}` — nova funcionalidade
- `fix/{issue-number}-{slug}` — correção de bug
- `chore/{slug}` — manutenção que não exige issue (ajustes de pom, .gitignore, etc.)
- `docs/{slug}` — apenas documentação

## Conventional commits

Formato: `<tipo>(<escopo>): descrição em pt-BR`

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`, `perf`, `ci`, `build`.

Escopo geralmente é o nome do módulo (`cpf-matcher`, `parent`, `repo`).

Exemplos:
- `feat(cpf-matcher): adicionar fallback para LDAP malformado`
- `test(cpf-matcher): cobrir cenário de CPF nulo`
- `chore(parent): atualizar versão do plugin maven-surefire`
- `docs(repo): adicionar guia de deploy em HML`

**Nunca adicionar `Co-Authored-By`** — commits são do desenvolvedor, sem atribuição de IA.

**Nunca usar `--no-verify`** para pular hooks.

## Padrões de código

### Naming

- **Identificadores Java em inglês** (convenção da linguagem): classes, métodos, variáveis, packages.
- **Mensagens de log e Javadoc descritivo** em pt-BR (alinhado a documentação do projeto Uni+).
- **Logs estruturados** com placeholders `{}` (SLF4J / JBoss Logging — Keycloak usa JBoss Logging por padrão).

### Package layout

`br.edu.unifesspa.uniplus.keycloak.<provider-name>`

Exemplo: `br.edu.unifesspa.uniplus.keycloak.cpfmatcher`.

### Testes

- **JUnit 5** + **Mockito**.
- Cobrir cenários explícitos descritos na issue como critérios de aceite.
- Arrange-Act-Assert nos testes — comentários `// arrange`, `// act`, `// assert` opcionais para clareza.
- Naming: `metodo_quandoCondicao_deveComportamento` (ex.: `actionImpl_quandoCpfTemFallback_deveAplicarAutoHeal`).

### Imports e formatação

- Sem wildcard imports.
- Indentação 4 espaços.
- Linhas até 120 caracteres.

## Build e validação local

Antes de abrir PR:

```bash
mvn clean verify
```

`verify` inclui compilação + testes. Falha em testes ou compilação **bloqueia** o PR.

## Pull Requests

- Título: mesmo formato de conventional commit.
- Descrição com seções **Summary**, **Test plan**, e referência `Closes #N`.
- PR pequeno e focado — uma Story por PR.
- Self-review antes de pedir review humano.

## Distribuição de providers

Os JARs deste repo são consumidos pelo `uniplus-api` (docker-compose para dev local) e pelo Keycloak institucional (Helm chart em ops, fora do escopo deste repo).

Mudanças nesta camada que afetem deploy precisam de coordenação com a Story de operação correspondente — descrever no PR e linkar issues coordenadas.

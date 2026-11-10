# Sequência de prompts para o Antigravity CLI (`agy`) - checkpoint R1 e R2

Regra de ouro: **um prompt = uma branch = um PR**. Rode `agy` na raiz do repositório (ele lê o `AGENTS.md` e as skills).
Antes: `git checkout develop && git pull && git checkout -b feature/<area>-<descricao>`.
Depois: `./mvnw verify` (ou `npm test`), revisar o diff linha a linha, colar o resumo da sessão no `LOG.md`, abrir PR (template), outro integrante revisa e faz squash merge.
Se o agente propuser algo fora da spec, mande: "Pare. Siga docs/specs ou liste a dúvida."

Ordem e dependências (quem faz: LE = Lucas Eduardo, LA = Lucas Albuquerque):
P0 (LE) -> P1 (LA); P2 já está FEITO (specs escritas com Claude, sem gastar o agy) -> P3 (LE) -> P4 (LE), P5 (LE), P6 (LA) em paralelo -> P7 (LA) -> P8a/b/c (ambos) -> P9 (ambos).

Dicas de economia: uma sessão nova do `agy` por prompt; modelo mais forte só em P3, P5, P6 (concorrência, outbox, estados); modelo mais barato em P0, P1, P7 e P8. Cada prompt já manda parar após 2 falhas iguais (ver AGENTS.md).

---
## P0 - Scaffold e infraestrutura (branch feature/infra-scaffold)
```
Leia AGENTS.md e docs/specs/scope-r1.md. Crie o monorepo conforme "Estrutura do repositório":
parent pom Maven multi-módulo (Java 21, Spring Boot 3, wrapper ./mvnw), módulos compiláveis e vazios para
api-gateway, iam-admin-service, inventory-service, order-service, ticket-service, analytics-service, realtime-service,
libs/security-lib e libs/messaging-lib; cada serviço com Actuator (/actuator/health), Flyway e pacotes hexagonais
(adapter.in, application, domain, adapter.out). Crie frontend/web com Vite + React + TypeScript (página "PulsePass" vazia).
Crie docker-compose.yml com: postgres (um banco por serviço via infra/init-db/init.sql: keycloak, iam, inventory, orders, tickets, analytics),
redis, kafka (KRaft), kafka-ui, keycloak (import de infra/keycloak/pulsepass-realm.json, pode ser um realm mínimo por enquanto),
e todos os serviços e o frontend com Dockerfile multi-stage e health checks. Crie .env.example, .gitignore, README com "como subir".
Não implemente regras de negócio. Critério: `docker compose up -d --build` sobe tudo saudável e `./mvnw verify` passa.
```
## P1 - CI no GitHub Actions (branch feature/infra-ci)
```
Leia AGENTS.md e a skill git-workflow. Crie .github/workflows/ci.yml disparado em pull_request e push para develop, com jobs:
lint (Spotless/Checkstyle no backend, ESLint+Prettier no frontend), test-unit (JUnit + JaCoCo com gate de 70% no pacote ..domain.. de cada serviço),
test-integration (Testcontainers; usar o Docker do runner), secrets-scan (gitleaks), build (Maven + npm) e docker-build (após os anteriores),
compose-smoke apenas em push para develop (docker compose up e aguarda health). Cache de Maven e npm. Configure Spotless/Checkstyle e JaCoCo nos poms.
Adicione badge de CI ao README. Documente em docs/pacote-2/pipeline.md como cada job mapeia para a pipeline do Pacote 2.
```
## P2 - Specs de API: FEITO (Claude, no chat)
Já estão em `docs/specs/`: `api-iam.md`, `api-inventory.md`, `api-order.md`, `api-realtime.md` e `acceptance-r1.md`. Não rode no agy. Se um prompt revelar contradição, ajuste a spec primeiro (humano + Claude) e só depois o código.

## P3 - Fundação: security-lib, messaging-lib, Keycloak e gateway (branch feature/gateway-foundation)
```
Leia AGENTS.md e as skills gateway-jwt, outbox-pattern, idempotent-consumer e testing-testcontainers, e docs/specs.
1) infra/keycloak/pulsepass-realm.json: realm pulsepass, roles CLIENTE, ORGANIZADOR, OPERADOR, ADMIN, clients pulsepass-spa (PKCE, redirect localhost:5173)
e pulsepass-api, usuários de seed (um por papel), registro de usuário habilitado, refresh token com rotação.
2) libs/security-lib: resource server JWT via JWKS, conversão de roles, utilitário para ler userId/roles.
3) libs/messaging-lib: envelope EventEnvelope, entidades/repositórios JDBC de outbox_event e processed_message, OutboxRelayWorker
(FOR UPDATE SKIP LOCKED, acks=all, idempotência do produtor), configuração de consumidor idempotente com retry 1s/2s/4s e DLT, e uma classe base AbstractIntegrationTest.
4) services/api-gateway: Spring Cloud Gateway com validação do JWT, denylist por jti no Redis, rate limit, rotas /api/iam, /api/inventory, /api/orders, /ws, rotas públicas do catálogo e health.
Testes: unitários e de integração (Testcontainers PG+Kafka+Redis) para outbox relay (publica, não perde ao reiniciar), consumidor idempotente (mesmo evento 3x, efeito 1x) e gateway (401 sem token, 403 com role errada, token na denylist rejeitado).
```
## P4 - IAM: iam-admin-service (branch feature/iam-admin)  [US-104, US-105]
```
Leia AGENTS.md, docs/specs/api-iam.md, acceptance-r1.md e as skills. Implemente iam-admin-service (hexagonal): cliente solicita virar organizador,
admin lista, aprova ou rejeita; ao aprovar, atribui o role ORGANIZADOR via Admin API do Keycloak (idempotente) e publica iam.organizer-approved
(e iam.organizer-requested na solicitação) pela outbox. Registra AuditoriaRegistro das decisões. Flyway. Domínio puro para a regra
PENDENTE -> APROVADA | REJEITADA com testes unitários (>= 70%). Integração com Testcontainers; o Keycloak Admin API pode ser simulado por WireMock.
```
## P5 - Inventário (executar em DUAS sessões)
### P5a - catálogo e eventos (branch feature/inventory-catalog)  [US-201..205]
```
Leia AGENTS.md, docs/specs/domain-model.md, api-inventory.md e acceptance-r1.md. Implemente APENAS no inventory-service: criação de evento, setores com preço e geração do mapa de assentos pelo organizador aprovado, publicação, catálogo público de leitura com cache no Redis e GET /events/{id}/seats. Flyway. Domínio sem Spring, testes unitários e de integração (Testcontainers). Não implemente reservas.
```
### P5b - reserva atômica e expiração (branch feature/inventory-reservation)  [US-301..305]
```
Leia AGENTS.md, a skill seat-locking, kafka-topics.md, api-inventory.md (seção reservas) e acceptance-r1.md. No inventory-service (com P5a integrado):
POST /reservations idempotente por reservationId (Redis Lua + UPDATE condicional no Postgres), DELETE /reservations/{id}, POST /reservations/{id}/confirm,
ExpireReservationsJob (varredura em banco, PT10M configurável, a cada 5 s) e eventos inventory.seats-reserved, inventory.seats-released e
inventory.reservation-expired pela outbox. ESCREVA PRIMEIRO os testes de concorrência: 500 threads disputando 1 assento (exatamente 1 vence);
reserva parcial não prende assento (nem no Redis); mesma reserva 3x tem efeito único; expiração devolve assentos e publica evento; confirm x expiração concorrentes (só um vence).
```
## P6 - Pedidos e checkout (executar em DUAS sessões)
### P6a - estados, idempotência e outbox (branch feature/order-core)  [US-401..403, 405]
```
Leia AGENTS.md, as skills outbox-pattern e idempotent-consumer, order-state-machine.md, api-order.md e acceptance-r1.md. Implemente no order-service:
Order com a máquina de estados no domínio puro (testes de toda transição válida e inválida, cobertura >= 70%), POST /orders e GET /orders/{id} com Idempotency-Key
(Redis fast path + Postgres, estados EM_PROCESSAMENTO e CONCLUIDO), InventoryClient (HTTP, timeout, circuit breaker; WireMock nos testes), outbox + relay para order.created. Sem pagamento ainda.
```
### P6b - pagamento e expiração (branch feature/order-payment)  [US-404, 406]
```
Leia AGENTS.md, api-order.md, api-inventory.md e acceptance-r1.md. No order-service (com P6a integrado): POST /orders/{id}/pay com Idempotency-Key usando o simulador de gateway
(latência e falha configuráveis; cartões de teste final 0002 recusa e 0004 timeout), chamada POST /reservations/{id}/confirm no inventory, transição para PAGO com order.paid
na outbox na mesma transação, 402/504 voltando a RESERVADO; consumidor idempotente de inventory.reservation-expired -> EXPIRADO + order.expired.
Testes: pagamento idempotente concorrente (uma cobrança) e consumo repetido 3x (efeito único).
```
## P7 - realtime-service (branch feature/realtime-seats)  [US-301]
```
Leia AGENTS.md, a skill realtime-stomp e docs/specs/api-realtime.md. Implemente realtime-service: STOMP em /ws autenticado por JWT, consumidores de
inventory.seats-reserved, inventory.seats-released, inventory.reservation-expired e order.expired publicando em /topic/events/{eventId}/seats e /user/queue/orders.
Testes de integração com Kafka em Testcontainers e cliente STOMP de teste.
```
## P8a - Frontend: base, login e catálogo (branch feature/front-base)  [US-101..103, 204, 205]
```
Leia AGENTS.md, docs/design/DESIGN.md, docs/mockups/ e docs/specs/api-*.md. Na SPA React+TS: tema Pulse Neon (tokens do DESIGN.md), login/cadastro Keycloak (OIDC PKCE, refresh),
rotas protegidas por role, cliente HTTP com Authorization e tratamento de problem+json, catálogo de eventos e detalhe do evento. Textos em pt-BR. Vitest + Testing Library.
```
## P8b - Frontend: mapa de assentos e checkout (branch feature/front-checkout)  [US-301..304, 401..404]
```
Leia AGENTS.md, DESIGN.md, mockups e api-*.md. Implemente o mapa de assentos com 4 estados (livre, selecionado, reservado, ocupado, com ícone/padrão além da cor), atualização em tempo real por STOMP,
seleção de vários assentos (máx. 8), checkout com contagem regressiva de 10 minutos e barra de progresso, POST /orders e /pay com Idempotency-Key (UUID por tentativa, reutilizado em reenvio),
confirmação simples e tratamento de expiração e de 402/409. Vitest + Testing Library.
```
## P8c - Frontend: organizador e administrador (branch feature/front-roles)  [US-104, 105, 201..203]
```
Leia AGENTS.md, DESIGN.md, mockups e api-*.md. Implemente: solicitação para virar organizador, tela do admin (pendentes, aprovar/rejeitar), área do organizador
(criar evento, setores/preços, gerar mapa, publicar). Vitest + Testing Library; E2E mínimo com Playwright do fluxo de compra (opcional se faltar cota).
```
## P9 - Integração do checkpoint e documentação viva (branch docs/checkpoint)
```
Leia AGENTS.md, a skill c4-docs e docs/specs/*.md (não leia PDFs). Suba o ambiente e verifique o fluxo completo (cadastro, login, reserva, pagamento, expiração).
Corrija apenas defeitos triviais de integração e liste os demais. Gere docs/pacote-3/divergencias.md comparando o código com docs/specs (serviços, tópicos, entidades, pipeline, endpoint /confirm)
e docs/pacote-3/estado-atual.md (pronto, pendente, conhecido). Atualize README e docs/c4/*.md (Mermaid) conforme o código.
```

---
# R2 (Pacote 4) - depois do checkpoint
P10 ticket-service [US-501..505] / P11 analytics-service e observabilidade [US-601..603] / P12 Frontend R2 (ingressos, painéis, histórico, cancelar) / P13 Carga k6 [US-705] / P14 Documentação final.
Os prompts de R2 serão detalhados após o checkpoint, com base no que o P9 revelar.

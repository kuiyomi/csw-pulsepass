# PulsePass - Instruções permanentes para agentes de IA

Plataforma distribuída de bilhetagem para megaeventos (flash crowd, zero overselling, reserva expira em 10 min).
Projeto acadêmico PUCRS - Construção de Software 2026/2. **Todo código é escrito por agentes de IA, sob revisão humana.**
A arquitetura é a descrita em `docs/pacote-2/` e `docs/c4/` (fonte de verdade). Em caso de conflito entre código e docs, PARE e pergunte.

## Escopo atual
Estamos construindo o **checkpoint (release R1, Pacote 3)**. Escopo em `docs/specs/scope-r1.md`.
Histórias R2 (ingressos/PDF/QR, e-mail, telemetria, painéis, carga) NÃO devem ser implementadas ainda, a menos que o prompt peça.

## Stack (decisões fixas; alterar só via ADR em docs/adr/)
- Backend: Java 21, Spring Boot 3.x, Maven multi-módulo (monorepo), Flyway
- API Gateway: Spring Cloud Gateway (único ponto de entrada; valida JWT, consulta denylist no Redis, rate limit)
- IAM: Keycloak (realm `pulsepass`, JWT RS256, roles CLIENTE, ORGANIZADOR, OPERADOR, ADMIN) + `iam-admin-service` (aprovação de organizadores, auditoria)
- Dados: PostgreSQL (um banco por serviço), Redis (locks de assento, cache do catálogo, idempotência rápida, denylist de tokens)
- Mensageria: Apache Kafka (KRaft) + Kafka UI
- Tempo real: `realtime-service` (Spring WebSocket/STOMP), alimentado por eventos Kafka
- Frontend: React + TypeScript + Vite (SPA), tema "Pulse Neon" (`docs/design/DESIGN.md`)
- Testes: JUnit 5, Mockito, Testcontainers (PostgreSQL, Kafka, Redis), JaCoCo, k6
- CI/CD: GitHub Actions. Orquestração local: `docker-compose.yml` na raiz.

## Estrutura do repositório
```
/services/api-gateway
/services/iam-admin-service
/services/inventory-service
/services/order-service
/services/ticket-service        (R2)
/services/analytics-service     (R2)
/services/realtime-service
/libs/security-lib              (validação JWT e mapeamento de roles, reutilizada)
/libs/messaging-lib             (envelope de evento, outbox relay, processed_message, DLT)
/frontend/web
/infra  (keycloak realm, k6, prometheus, init-db)
/docs   (specs, adr, c4, ai-process, pacote-1, pacote-2, pacote-3, mockups, design)
```
Cada serviço usa arquitetura hexagonal: `adapter.in` (controllers, consumers) -> `application` (use cases) -> `domain` -> `adapter.out` (repositories, clients, producers). O domínio NÃO depende de Spring.

## Regras inegociáveis
1. **Zero overselling**: alocação atômica de assento (skill `seat-locking`). PostgreSQL é a verdade; Redis é otimização.
2. **Reserva expira em 10 min** (`PT10M`, configurável). A varredura em banco garante a expiração. Expirar devolve assentos e publica evento.
3. **Reserva é HTTP síncrono** `order-service -> inventory-service`, com `reservationId = orderId` (repetir a chamada não duplica efeito). Timeout + circuit breaker no cliente. Todo o resto entre serviços é por eventos Kafka.
4. **Dual-write proibido**: nunca grave no banco e publique no Kafka em passos separados. Use Transactional Outbox (skill `outbox-pattern`). Exceção documentada: telemetria de navegação (R2).
5. **Consumidores idempotentes** (skill `idempotent-consumer`): `processed_message` na mesma transação do efeito.
6. `POST /orders` e `POST /orders/{id}/pay` exigem header `Idempotency-Key`.
7. Máquina de estados do pedido: `CRIADO -> RESERVADO -> AGUARDANDO_PAGAMENTO -> PAGO | EXPIRADO`. Transições inválidas lançam exceção de domínio (`docs/specs/order-state-machine.md`).
8. Toda rota exceto catálogo público de leitura e health exige JWT válido + role (skill `gateway-jwt`). Serviços também validam o JWT (defesa em profundidade) via `security-lib`.
9. Um banco por serviço; referência entre serviços só por identificador (sem FK entre bancos).

## Convenções
- Código e identificadores em inglês; documentação, mensagens de commit e textos de UI em português.
- Commits: Conventional Commits. Branches e PR: skill `git-workflow`.
- Migrations Flyway versionadas; nunca editar migration já commitada.
- Eventos Kafka: JSON com envelope `{eventId, eventType, occurredAt, aggregateId, version, payload}`; tópicos em `docs/specs/kafka-topics.md` (nomes exatos).
- Sem segredos no repositório; `.env.example` + variáveis de ambiente.
- Logs JSON com `traceId`; Actuator + Micrometer em todos os serviços; `/actuator/health` usado nos health checks do compose.
- Erros HTTP em formato `application/problem+json` (RFC 7807).

## Fluxo de trabalho do agente (spec-driven)
1. Leia a spec relevante em `docs/specs/` antes de codar. Se faltar spec ou houver ambiguidade, PARE e pergunte (não invente comportamento).
2. Testes primeiro (ou junto). Domínio com cobertura >= 70% (JaCoCo falha o build abaixo).
3. Implemente em passos pequenos. Rode `./mvnw -q verify` (backend) ou `npm test` (frontend) antes de dizer que terminou.
4. Atualize docs afetadas (specs, C4, kafka-topics) no mesmo PR.
5. Ao final, escreva um resumo para o `docs/ai-process/LOG.md` (o humano cola): objetivo, o que foi gerado, decisões, pendências.
6. Nunca invente dependências ou versões: verifique no pom/package.json; em dúvida, pergunte.
7. Não faça push em `main`/`develop`; trabalhe na branch de feature atual.

## Economia de contexto e de tokens
- Leia SOMENTE o que o prompt e a spec citam. Não varra o repositório nem leia PDFs, `docs/pacote-*` ou `docs/c4/*.png`; as specs em `docs/specs/*.md` são suficientes.
- Rode testes só do módulo que você alterou: `./mvnw -q -pl <modulo> -am verify`. O `docker compose` completo só no P0 e no P9.
- Se o mesmo teste ou build falhar 2 vezes seguidas, PARE e reporte o erro e sua hipótese; não entre em laço de tentativas.
- Não reescreva arquivos inteiros para mudar poucas linhas; faça edições pontuais. Não gere documentação além da pedida.
- Entregue em passos pequenos: ao terminar cada parte do prompt, faça `./mvnw -q -pl <modulo> verify`, resuma em 5 linhas e siga.

## Comandos
- Subir tudo: `docker compose up -d --build`
- Testes backend: `./mvnw verify`
- Testes frontend: `cd frontend/web && npm test`
- Carga (R2): `k6 run infra/k6/flash-sale.js`

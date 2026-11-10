# Escopo do checkpoint (R1, Pacote 3)

Fonte: Pacote 1, tabelas de histórias. Release R1 = checkpoint; R2 = produto completo (Pacote 4).

| Épico | Histórias em R1 | Fora do R1 (R2) |
|---|---|---|
| E1 Identidade | US-101, 102, 103, 104, 105 | US-106 auditoria |
| E2 Catálogo | US-201, 202, 203, 204, 205 | US-206 agendar abertura |
| E3 Inventário | US-301, 302, 303, 304, 305 | US-306 alta leitura sem degradar reservas |
| E4 Pedidos | US-401, 402, 403, 404, 405, 406 | US-407 cancelar, US-408 histórico |
| E5 Ingressos | nenhuma | US-501 a 505 |
| E6 Telemetria | nenhuma | US-601 a 603 |
| E7 DevOps | US-701, 702, 703, 704, 706 | US-705 carga |

## Consequências
- `ticket-service` e `analytics-service` ficam apenas como módulos vazios e compiláveis (health OK). `order.paid` já é publicado; ninguém o consome ainda.
- `realtime-service` existe em R1 só para US-301 (mapa de assentos em tempo real) e atualização do status do pedido.
- O SMTP (MailHog) pode ficar fora do compose até o R2.
- Frontend R1: login/cadastro, catálogo, detalhe do evento, mapa de assentos com tempo real, checkout com contagem regressiva, confirmação simples (sem PDF), solicitação de organizador, tela do admin para aprovar, criação de evento/lotes/mapa pelo organizador.

## Definição de pronto do checkpoint
1. `docker compose up -d --build` sobe tudo saudável.
2. Fluxo completo funciona na SPA: cadastro -> login -> escolher assentos -> reservar -> pagar (gateway simulado) -> pedido PAGO.
3. Reserva não paga expira em 10 min, assentos voltam e o pedido vira EXPIRADO.
4. Teste de concorrência prova zero sobre-venda; testes de Outbox e idempotência passam.
5. CI verde em `develop` (lint, unit com JaCoCo >= 70% no domínio, integração Testcontainers, build).
6. `docs/pacote-1` e `docs/pacote-2` atualizados; `LOG.md` com todas as sessões; tag `checkpoint-1`.

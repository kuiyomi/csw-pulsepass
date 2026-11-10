# Tópicos Kafka (fonte: Pacote 2, seção 5)

Padrão de nome: `contexto.evento-no-passado`. Cada serviço consome com `group.id` = nome do serviço. Cada tópico consumido tem `<topico>.DLT` com o mesmo número de partições.

| Tópico | Produtor | Consumidores | Chave | Partições | Release |
|---|---|---|---|---|---|
| inventory.seats-reserved | inventory-service | realtime-service, analytics-service | reservaId | 12 | R1 |
| inventory.seats-released | inventory-service | realtime-service | reservaId | 12 | R1 |
| inventory.reservation-expired | inventory-service | order-service | reservaId | 12 | R1 |
| order.created | order-service | analytics-service | pedidoId | 6 | R1 (publica) |
| order.paid | order-service | ticket-service, realtime-service, analytics-service | pedidoId | 6 | R1 (publica) |
| order.expired | order-service | realtime-service, analytics-service | pedidoId | 6 | R1 |
| ticket.issued | ticket-service | realtime-service, analytics-service | pedidoId | 6 | R2 |
| iam.organizer-requested | iam-admin-service | analytics-service | solicitacaoId | 3 | R1 (publica) |
| iam.organizer-approved | iam-admin-service | iam-admin-service, analytics-service | solicitacaoId | 3 | R1 |
| telemetry.navigation | analytics-service (ingestão) | analytics-service | sessionId | 6 | R2 |
| analytics.metrics | analytics-service | realtime-service | eventoId | 3 | R2 |

Em desenvolvimento: 1 broker, replicação 1 (produção seria 3).

## Envelope (todas as mensagens)
```json
{ "eventId": "uuid", "eventType": "order.paid", "occurredAt": "2026-11-20T18:30:00Z",
  "aggregateId": "pedidoId", "version": 1, "payload": { } }
```
`eventId` é a base da idempotência. `aggregateId` = chave da mensagem.

## Payloads mínimos
- `inventory.seats-reserved`: `{reservaId, pedidoId, eventoId, usuarioId, assentos:[{assentoId, setorId}], expiraEm}`
- `inventory.seats-released`: `{reservaId, eventoId, assentoIds, motivo: CANCELAMENTO|EXPIRACAO}`
- `inventory.reservation-expired`: `{reservaId, pedidoId, eventoId, assentoIds}`
- `order.created`: `{pedidoId, usuarioId, eventoId, total}`
- `order.paid`: `{pedidoId, usuarioId, eventoId, itens:[{assentoId, setorNome, preco}], total, pagoEm}`
- `order.expired`: `{pedidoId, usuarioId, eventoId}`
- `iam.organizer-requested`: `{solicitacaoId, usuarioId}`; `iam.organizer-approved`: `{solicitacaoId, usuarioId, decididoPor, decididoEm}`

## Configuração
- Produtor (relay do Outbox): `acks=all`, `enable.idempotence=true`, chave = aggregateId.
- Consumidor: `enable.auto.commit=false`, commit manual após a transação; 3 tentativas com backoff exponencial (1s, 2s, 4s) e depois DLT.
- Retenção: 7 dias nos tópicos de domínio, 14 dias nos DLTs, 1 dia em `telemetry.navigation`.

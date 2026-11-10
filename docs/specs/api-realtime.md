# realtime-service (WebSocket/STOMP)

Endpoint WebSocket: `ws://localhost:8080/ws` (via gateway). Handshake e CONNECT exigem JWT válido (header STOMP `Authorization: Bearer <jwt>` ou query `?access_token=`); inválido -> conexão recusada.

## Destinos
- `/topic/events/{eventId}/seats` (qualquer usuário autenticado). Mensagem:
```json
{ "type": "SEATS_RESERVED | SEATS_RELEASED | RESERVATION_EXPIRED", "eventId": "uuid", "seatIds": ["uuid"], "status": "RESERVADO | LIVRE", "occurredAt": "ISO-8601" }
```
- `/user/queue/orders` (só o dono do pedido). Mensagem:
```json
{ "type": "ORDER_EXPIRED", "orderId": "uuid", "status": "EXPIRADO", "occurredAt": "ISO-8601" }
```
(R2 acrescenta `ORDER_PAID` e `TICKET_ISSUED`.)

## Consumo
Tópicos (grupo `realtime-service`, idempotente por `eventId` em memória com janela, pois a repetição é inofensiva): `inventory.seats-reserved`, `inventory.seats-released`, `inventory.reservation-expired`, `order.expired`.
Mapeamento: seats-reserved -> SEATS_RESERVED/RESERVADO; seats-released e reservation-expired -> LIVRE; `order.expired` -> destino do usuário (`usuarioId` do payload).
Mensagens perdidas são aceitáveis: ao reconectar, a SPA recarrega `GET /events/{id}/seats` e depois volta a ouvir o tópico.

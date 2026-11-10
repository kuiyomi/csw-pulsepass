# ADR 0004 - Reserva por HTTP síncrono order -> inventory
- Status: aceita
- Data: 2026-10

## Contexto
O pedido precisa saber na hora se os assentos foram garantidos.

## Decisão
Chamada HTTP síncrona com reservationId = orderId (idempotente), timeout e circuit breaker. Expiração e demais fluxos por eventos.

## Consequências
Acoplamento temporal limitado a essa chamada; a expiração em 10 min funciona como compensação.

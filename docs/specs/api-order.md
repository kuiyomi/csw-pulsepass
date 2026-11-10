# API do order-service (`/api/orders`)

Auth: JWT, role CLIENTE. `Idempotency-Key` (UUID) obrigatório nos POST; ausente -> 400.
Taxa de serviço = 10% do subtotal (arredondada para cima, em centavos).

## POST /orders
Corpo `{ "eventoId": "uuid", "assentoIds": ["uuid", "..."] }` (1 a 8 assentos)
Fluxo: grava o `IdempotencyRecord` (EM_PROCESSAMENTO, com orderId) -> cria Pedido `CRIADO` -> `POST /reservations` no inventory com `reservationId = orderId` -> `RESERVADO` -> `order.created` na outbox -> conclui o registro de idempotência com a resposta.
- 201 `{ "id","status":"RESERVADO","expiraEm","itens":[{"assentoId","setorNome","preco"}],"subtotal","taxa","total" }`
- 409 assentos indisponíveis: Pedido fica `EXPIRADO` (motivo SEM_ASSENTOS); problem+json com `pedidoId` e `assentosIndisponiveis`. A resposta é guardada e repetida para a mesma chave.
- 503 inventory indisponível/circuit breaker aberto: a resposta NÃO é guardada; repetir com a mesma chave continua o mesmo pedido (a reserva é idempotente pelo orderId).
- Mesma chave e mesmo corpo concluído: devolve a resposta original (cabeçalho `Idempotency-Replayed: true`). Mesma chave e corpo diferente: 422. Mesma chave ainda EM_PROCESSAMENTO: 409 com `Retry-After: 1`.

## GET /orders/{id}
200 com o mesmo formato do 201 mais `pagamento` (se houver). Somente o dono ou ADMIN; senão 404.

## POST /orders/{id}/pay
Corpo `{ "metodo": "CARTAO_SIMULADO", "numeroCartao": "4111111111111111" }` (simulador; nunca persistir o número completo, só os 4 últimos)
Fluxo: exige `RESERVADO` (ou repetição idempotente) -> `AGUARDANDO_PAGAMENTO` -> gateway simulado -> aprovado: confirma a reserva no inventory (`/confirm`), `PAGO` e `order.paid` na mesma transação da mudança de estado -> resposta.
- 200 `{ "id","status":"PAGO","pagoEm" }`
- 402 pagamento recusado: volta a `RESERVADO` (a reserva continua válida até `expiraEm`); problem+json.
- 409 pedido `EXPIRADO` ou em estado inválido para pagar.
- Idempotency-Key repetida e concluída: mesma resposta; nunca cobra duas vezes. Duas requisições concorrentes com a mesma chave: uma processa, a outra recebe 409 `Retry-After`.
- Falha do gateway por timeout: 504, pedido volta a `RESERVADO`, nova tentativa permitida com NOVA chave.

## Simulador do gateway de pagamento (configuração)
`payment.simulator.latency-ms` (padrão 300), `payment.simulator.failure-rate` (0.0 a 1.0, padrão 0.0). Cartões de teste: terminado em `0002` sempre recusa; terminado em `0004` sempre dá timeout; demais aprovam.

## Consumidor
`inventory.reservation-expired` (grupo `order-service`): se o pedido está RESERVADO ou AGUARDANDO_PAGAMENTO -> `EXPIRADO` + `order.expired` na outbox; se já `PAGO` ou `EXPIRADO`: ignora; sempre registra em `processed_message` na mesma transação.

## Eventos publicados (outbox)
`order.created`, `order.paid`, `order.expired` (ver kafka-topics.md). Reserva no inventory: ver api-inventory.md.

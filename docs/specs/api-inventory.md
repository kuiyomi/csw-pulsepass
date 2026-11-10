# API do inventory-service (`/api/inventory`)

Valores monetários em centavos (inteiro). Datas em ISO-8601 UTC. Estados do assento: `LIVRE`, `RESERVADO`, `VENDIDO`. Estados do evento: `RASCUNHO`, `PUBLICADO`.
"Lote" do backlog = setor com preço (um organizador pode criar vários setores/fases).

## Públicas (sem JWT)
- `GET /events?q=&page=0&size=20` -> 200 `{ "itens": [ {"id","nome","dataHora","local","status","precoMinimo","disponibilidade":"ABERTA|ESGOTADO"} ], "pagina", "total" }` (só PUBLICADO). Cache Redis (TTL 30 s).
- `GET /events/{id}` -> 200 `{ "id","nome","descricao","dataHora","local","setores":[{"id","nome","preco","capacidade","livres","acessivel"}] }`. 404 se inexistente ou não publicado.

## Cliente autenticado
- `GET /events/{id}/seats` (CLIENTE) -> 200 `{ "eventoId", "setores":[{"setorId","nome","preco","assentos":[{"id","fileira","numero","status"}]}] }`. O status `RESERVADO` vale para qualquer reserva ativa, inclusive a do próprio usuário (a SPA diferencia "selecionado" localmente).

## Organizador (ORGANIZADOR aprovado e dono do evento)
- `POST /events` `{ "nome","descricao","dataHora","local" }` -> 201 evento `RASCUNHO`. 403 se não for ORGANIZADOR.
- `POST /events/{id}/sectors` `{ "nome","preco","fileiras": 1-50,"assentosPorFileira": 1-100,"acessivel": false }` -> 201 `{ "setorId","assentosCriados" }`. Fileiras nomeadas A, B, C...; só em RASCUNHO.
- `POST /events/{id}/publish` -> 200 `PUBLICADO`; 409 se não houver setor ou já publicado; invalida o cache do catálogo.
- `GET /organizer/events` -> eventos do organizador autenticado, com contagem de vendidos/reservados.

## Interno: reservas (somente order-service)
O gateway NÃO expõe `/api/inventory/reservations/**`. O order-service chama `http://inventory-service:8080/reservations` encaminhando o `Authorization` do usuário.
- `POST /reservations` `{ "reservationId": "uuid (= orderId)", "eventoId", "usuarioId", "assentoIds": ["..."] }`
  - 201 `{ "reservationId","expiraEm","assentos":[{"assentoId","setorId","setorNome","preco"}] }` (nova)
  - 200 mesmo corpo (reserva já existente com o mesmo `reservationId`, sem novo efeito)
  - 409 problem+json com `"assentosIndisponiveis": ["..."]` (nenhum assento fica preso)
  - 404 evento inexistente ou não publicado; 400 lista vazia ou mais de 8 assentos.
- `DELETE /reservations/{reservationId}` -> 204 (também se já inexistente ou já liberada). Devolve assentos RESERVADO -> LIVRE e publica `inventory.seats-released` (motivo CANCELAMENTO).
- `POST /reservations/{reservationId}/confirm` -> 204: marca os assentos VENDIDO e conclui a reserva (chamado pelo order-service ao pagar). Se a reserva já expirou: 409.

## Eventos publicados (outbox)
`inventory.seats-reserved`, `inventory.seats-released`, `inventory.reservation-expired` (ver kafka-topics.md).

## Regras
- Reserva dura `PT10M` (propriedade `inventory.reservation.ttl`). `ExpireReservationsJob` roda a cada 5 s.
- Máximo de 8 assentos por reserva. Reserva é tudo-ou-nada.
- Confirmar reserva e expirar a mesma reserva competem por uma atualização condicional na linha da reserva (status ATIVA): só um vence.

## Nota de divergência (registrar em docs/pacote-3/divergencias.md)
O Pacote 2 não detalha a confirmação da reserva. Esta spec adiciona `POST /reservations/{id}/confirm` (assento -> VENDIDO) como parte da chamada síncrona order -> inventory. Nenhum tópico novo foi criado: o mapa em tempo real continua mostrando o assento como indisponível (RESERVADO) até a recarga por REST.

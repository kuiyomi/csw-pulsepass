# Convenções de API (preencher/estender em api-<servico>.md)

- Entrada única: `api-gateway` em `:8080`. Rotas `/api/iam/**`, `/api/inventory/**`, `/api/orders/**`, `/ws/**`.
- Auth: `Authorization: Bearer <JWT>`; roles CLIENTE, ORGANIZADOR, OPERADOR, ADMIN. Catálogo `GET /api/inventory/events` e `GET /api/inventory/events/{id}` são públicos.
- Erros: `application/problem+json` (`type`, `title`, `status`, `detail`, `traceId`). 409 conflito de assento, 422 Idempotency-Key reutilizada com corpo diferente, 400 validação.
- Datas ISO-8601 UTC; dinheiro como inteiro em centavos + `moeda: "BRL"`.
- Idempotency-Key: UUID no header; resposta repetida inclui `Idempotency-Replayed: true`.

## Endpoints mínimos R1
**inventory** (`/api/inventory`): `GET /events`, `GET /events/{id}`, `GET /events/{id}/seats`, `POST /events` (ORG), `POST /events/{id}/sectors` (ORG), `POST /events/{id}/publish` (ORG aprovado), `POST /reservations` (interno, usado pelo order), `DELETE /reservations/{id}`.
**order** (`/api/orders`): `POST /orders` (CLIENTE, Idempotency-Key), `GET /orders/{id}`, `POST /orders/{id}/pay` (CLIENTE, Idempotency-Key).
**iam-admin** (`/api/iam`): `POST /organizer-requests` (CLIENTE), `GET /organizer-requests?status=` (ADMIN), `POST /organizer-requests/{id}/approve|reject` (ADMIN).
Cadastro e login de usuário: direto no Keycloak (OIDC com PKCE na SPA).

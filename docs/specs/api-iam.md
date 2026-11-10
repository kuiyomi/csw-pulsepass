# API do iam-admin-service (`/api/iam`)

Cadastro, login, refresh e logout são feitos direto no Keycloak (OIDC, PKCE). Este serviço trata só a governança de organizadores.
Erros: `application/problem+json`. Auth: JWT Bearer. O `userId` vem do claim `sub`.

| Método e caminho | Role | Descrição |
|---|---|---|
| POST `/organizer-requests` | CLIENTE | Solicita virar organizador |
| GET `/organizer-requests?status=PENDENTE&page=0&size=20` | ADMIN | Lista solicitações |
| POST `/organizer-requests/{id}/approve` | ADMIN | Aprova e atribui o role ORGANIZADOR |
| POST `/organizer-requests/{id}/reject` | ADMIN | Rejeita |

## POST /organizer-requests
Corpo: `{ "nomeFantasia": "string 3-80", "documento": "string 11-18", "justificativa": "string até 500" }`
- 201 `{ "id": "uuid", "status": "PENDENTE", "criadaEm": "..." }`; publica `iam.organizer-requested` (outbox).
- 409 se o usuário já tem solicitação PENDENTE ou APROVADA. 403 se não for CLIENTE.

## GET /organizer-requests
200 `{ "itens": [ {"id","usuarioId","nomeFantasia","documento","status","criadaEm"} ], "pagina": 0, "total": 3 }`

## POST /organizer-requests/{id}/approve
- 200 `{ "id", "status": "APROVADA", "decididoPor", "decididoEm" }`. Atribui o role via Keycloak Admin API; grava AuditoriaRegistro; publica `iam.organizer-approved` (outbox), tudo coerente em caso de repetição.
- Repetir em solicitação já APROVADA devolve 200 sem novo efeito. Solicitação REJEITADA: 409. Inexistente: 404.
- Falha do Keycloak: 502; a solicitação permanece PENDENTE e a chamada pode ser repetida.
- O usuário precisa renovar o token (novo login ou refresh) para ver o novo role.

## POST /organizer-requests/{id}/reject
Corpo `{ "motivo": "string 5-300" }`. 200 `{ "status": "REJEITADA", ... }`. Já REJEITADA: 200 idempotente; APROVADA: 409.

## Consumidor
`iam.organizer-approved` (grupo `iam-admin-service`): consumo idempotente para completar a atribuição de role caso a chamada ao Keycloak tenha ficado pendente (reconciliação).

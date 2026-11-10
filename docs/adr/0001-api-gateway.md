# ADR 0001 - API Gateway como ponto único de entrada
- Status: aceita
- Data: 2026-10

## Contexto
A SPA precisa de uma única URL e a validação de JWT deve ocorrer na borda.

## Decisão
Usar Spring Cloud Gateway (api-gateway), com validação de JWT, denylist no Redis, roteamento e rate limit.

## Consequências
Um contêiner a mais para manter; serviços ainda validam o JWT (defesa em profundidade).

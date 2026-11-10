# ADR 0003 - realtime-service separado
- Status: aceita
- Data: 2026-10

## Contexto
Atualizações em tempo real não devem se acoplar à emissão de ingressos.

## Decisão
Serviço próprio com WebSocket/STOMP que consome eventos do Kafka.

## Consequências
Mais um serviço; desacopla o tempo real do restante.

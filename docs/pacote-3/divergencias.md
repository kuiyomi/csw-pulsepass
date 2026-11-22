# Desenho de R1 e implementação observada

| Área | Já se encontra no código | Pendente para a demonstração de R1 |
|---|---|---|
| Identidade | Realm, papéis, cadastro e rotação configurados. Regra de decisão de organizadores testada. | Login na SPA, autorização, revogação, API de governança e auditoria. |
| Inventário | API de leitura, telas de catálogo e detalhe, migration, comandos condicionais de alocação e liberação e scripts Redis. | Criação e publicação por organizador, cache do catálogo, endpoint de reserva, varredura de expiração e atualização do mapa por STOMP. |
| Pedidos | Estados e valores testados. A especificação de confirmação síncrona detalha a transição da reserva para venda. | Rotas de pedidos e pagamento, idempotência HTTP, ligação ao inventário e confirmação de reserva pelo endpoint. |
| Mensageria | Migration, adaptadores de outbox e processamento idempotente, script dos tópicos e testes de integração. | Publicação e consumo pelos serviços em uma compra real; execução dos testes com broker e banco. |
| Operação | Maven Wrapper, imagens descritas por Dockerfile, Compose e workflow. | Inicialização integrada, lint completo, CI remota e revisão da entrega. |
| R2 | Regra de domínio que recusa a reutilização de um ingresso. | Emissão de PDF e QR, leitura persistente na entrada, e-mail e painéis. |

O Pacote 2 descreve revogação por jti no Redis. Para a versão completa, é preciso demonstrar também como o armazenamento em chave-valor participa do ciclo do refresh token exigido no enunciado. A lista de revogação de tokens de acesso, isoladamente, não comprova esse requisito.

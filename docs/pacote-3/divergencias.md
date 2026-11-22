# Implementação de R1 e verificações pendentes

| Área | Implementação da revisão | O que conferir na demonstração |
|---|---|---|
| Identidade | Cadastro e login no Keycloak por PKCE, renovação na SPA, papéis, revogação do token de acesso no gateway, solicitação de organizador, decisão administrativa e auditoria. | Cadastro com novo usuário, rotação do refresh token, acesso após logout e concessão do papel. |
| Catálogo | Evento em rascunho, setores, assentos, publicação, consulta paginada e cache Redis de 30 segundos. | Conferir que o rascunho fica oculto e a publicação aparece no catálogo. |
| Inventário | Bloqueio Redis em grupo e atualização SQL condicional na mesma transação de negócio da reserva e da outbox. Confirmação, cancelamento interno e expiração. | Disputa por um lugar, expiração após dez minutos, ausência de bloqueios residuais e atualização do segundo mapa em até dois segundos. |
| Pedido | Reserva, cálculo em centavos, taxa de 10%, idempotência e pagamento simulado. | Conferir aprovação, recusa, timeout e repetição da chave. |
| Mensageria | Publicação da outbox no Kafka, consumo idempotente da expiração no serviço de pedidos e notificações STOMP. | Testar interrupção do broker, retomada e sincronização após reconectar. |
| Operação | Pipeline de verificações, ambiente Compose, dados de demonstração e testes pela API e pelo navegador. | Execução da CI no GitHub e proteção de branch. |

O Keycloak gerencia os refresh tokens. O Redis mantém a lista de revogação de tokens de acesso, os bloqueios de assentos e o cache do catálogo. A persistência específica de refresh tokens em armazenamento chave-valor continua uma diferença em relação ao desenho.

A confirmação da venda usa a chamada interna de pedidos para inventário prevista no contrato de R1. A próxima entrega (Pacote 4) abrange emissão de ingressos em PDF com QR, e-mail, validação na entrada e telemetria com painéis, além das demais histórias R2 do escopo.

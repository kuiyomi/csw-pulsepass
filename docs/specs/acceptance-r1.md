# Critérios de aceite do checkpoint (R1)

Formato Dado/Quando/Então. Cada critério deve virar pelo menos um teste automatizado (unitário, integração ou E2E).

## E1 - Identidade
- **US-101** Dado um visitante, quando se cadastra e faz login na SPA, então recebe JWT com role CLIENTE e acessa as rotas de cliente.
- **US-102** Dado um token de acesso expirado e um refresh token válido, quando a SPA renova a sessão, então recebe novo par de tokens sem novo login; o refresh token antigo é rejeitado (rotação).
- **US-103** Dado qualquer rota protegida, quando chamada sem token, com token inválido/expirado ou sem o role exigido, então responde 401 ou 403; token com `jti` na denylist é rejeitado. Catálogo público e health respondem sem token.
- **US-104** Dado um CLIENTE, quando solicita ser organizador, então cria solicitação PENDENTE e publica `iam.organizer-requested`; segunda solicitação enquanto PENDENTE responde 409.
- **US-105** Dado um ADMIN, quando aprova uma solicitação, então o usuário passa a ter o role ORGANIZADOR, há registro de auditoria e `iam.organizer-approved` é publicado; aprovar de novo não duplica efeito; rejeitar exige motivo.

## E2 - Catálogo
- **US-201** Dado um ORGANIZADOR aprovado, quando cria evento com nome, data, local e descrição, então o evento nasce RASCUNHO e pertence a ele; um CLIENTE recebe 403.
- **US-202** Dado um evento RASCUNHO, quando o organizador adiciona setores com preço, então ficam disponíveis com preços distintos; setor com preço <= 0 responde 400.
- **US-203** Dado um setor com N fileiras e M assentos por fileira, então são criados N x M assentos LIVRE com fileira e número únicos.
- **US-204** Dado eventos publicados, quando o visitante lista e busca por texto, então só vê eventos PUBLICADO, paginados; a segunda leitura vem do cache.
- **US-205** Dado um evento publicado, quando o cliente abre o detalhe, então vê setores, preços e livres por setor; evento RASCUNHO responde 404.

## E3 - Inventário
- **US-301** Dado um cliente com o mapa aberto, quando outro usuário reserva ou libera um assento, então o mapa dele atualiza em até 2 s via STOMP sem recarregar.
- **US-302** Dado uma reserva de N assentos em que um deles está ocupado, então a reserva falha com 409 e NENHUM dos assentos fica RESERVADO (nem no Redis).
- **US-303 (zero sobre-venda)** Dado um assento livre e 500 requisições concorrentes de reserva, então exatamente 1 vence e as demais recebem 409; o assento nunca consta reservado ou vendido para duas pessoas.
- **US-304** Dado uma reserva criada em T, então `expiraEm = T + 10 min` é devolvido ao cliente, que mostra contagem regressiva; reserva é tudo-ou-nada.
- **US-305** Dado uma reserva não paga, quando T + 10 min é atingido (ou o pedido é cancelado), então os assentos voltam a LIVRE em uma transação, `inventory.reservation-expired` (ou `seats-released`) é publicado e os assentos podem ser reservados por outro usuário.

## E4 - Pedidos
- **US-401** Dado uma reserva válida, quando o cliente cria o pedido com `Idempotency-Key`, então recebe pedido RESERVADO com itens, subtotal, taxa de 10% e total.
- **US-402** Dado a máquina de estados, então toda transição válida do diagrama é permitida e toda inválida (por exemplo PAGO -> RESERVADO, EXPIRADO -> PAGO) lança exceção de domínio; cobertura de domínio >= 70%.
- **US-403 (idempotência)** Dado duas requisições com a mesma `Idempotency-Key` e o mesmo corpo (inclusive concorrentes), então existe um único pedido e uma única cobrança, e ambas recebem a mesma resposta; mesma chave com corpo diferente responde 422.
- **US-404** Dado um pedido RESERVADO, quando o cliente paga com cartão válido, então vai a PAGO; com cartão recusado, responde 402 e o pedido volta a RESERVADO; com timeout do gateway, responde 504 e volta a RESERVADO.
- **US-405 (Outbox)** Dado um pedido persistido, então o evento correspondente existe em `outbox_event` na MESMA transação; com o Kafka fora do ar o pedido continua válido e o evento é publicado depois, sem perda e sem quebrar a ordem por pedido; se a transação falha, nenhuma linha de outbox existe.
- **US-406** Dado um pedido RESERVADO ou AGUARDANDO_PAGAMENTO cuja reserva venceu, quando chega `inventory.reservation-expired`, então o pedido vira EXPIRADO e `order.expired` é publicado; o mesmo evento entregue 3 vezes tem efeito único; pedido PAGO ignora o evento.

## E7 - Qualidade e processo
- **US-701** `docker compose up -d --build` sobe todos os contêineres saudáveis com um único comando.
- **US-702** Todo PR e todo push em `develop` executam lint, testes, cobertura e build; PR com CI vermelha não pode ser integrado.
- **US-703** O build falha se a cobertura do domínio de algum serviço ficar abaixo de 70%.
- **US-704** Existem testes de integração com PostgreSQL, Kafka e Redis reais (Testcontainers) para reserva concorrente, outbox, idempotência HTTP e consumo idempotente.
- **US-706** Cada sessão de IA tem uma linha no `LOG.md` (data, autor, prompt, resultado, correções humanas, PR).

# PulsePass

Plataforma distribuída de bilhetagem para megaeventos. Projeto de Construção de Software da PUCRS, 2026/2.

**[Relatório do Pacote 3 para entrega no Moodle (PDF)](docs/pacote-3/Pacote_3_PulsePass.pdf)**

O PDF reúne o checkpoint e os relatórios atualizados dos Pacotes 1 e 2.

- [Documentação e anexos do checkpoint](docs/pacote-3/README.md)
- [Estado atual e instruções de execução](docs/pacote-3/estado-atual.md)
- [Especificações dos serviços e dos critérios de aceite](docs/specs/)

## Executar no Mac

Na raiz deste repositório, com Docker Desktop aberto:

```bash
test -f .env || cp .env.example .env
docker compose up -d --build --wait --wait-timeout 300
open http://localhost:5173
```

O Compose carrega os eventos **Festival Orla** e **Noite Instrumental** depois das migrações e preserva reservas de execuções anteriores. O catálogo é público. Entre com o usuário `cliente` e a senha de demonstração `pulsepass-demo-local` (ou o valor personalizado de `PULSEPASS_DEMO_PASSWORD` usado ao criar a conta). Abra um evento, selecione de um a oito lugares e confira o total com taxa de 10%. O cartão fictício `4111111111111111` aprova a compra. Finais `0002` e `0004` simulam recusa e timeout.

O perfil `organizador` pode criar eventos, gerar setores e publicar. O perfil `admin-pulse` analisa solicitações de organizador. As contas de demonstração usam a mesma senha local. Para testar a aprovação, crie uma conta de cliente pela tela inicial, solicite o perfil e abra a conta administrativa em outro navegador. Após a aprovação, use **Atualizar perfil** na conta do cliente.

## Conferir antes do envio

```bash
./mvnw -Pintegration clean verify
python3 scripts/testar-compra.py
```

O primeiro comando usa Java 21 e Docker. O segundo usa Python 3 e requer o Compose em execução. Ele cria um evento de teste, verifica publicação, pagamentos e repetição de requisições, e disputa um assento com 500 pedidos. O resultado esperado é uma reserva e 499 conflitos. A primeira execução requer internet para baixar as dependências. Para ver qual serviço falhou na inicialização, execute `docker compose ps -a` e `docker compose logs --tail=80 NOME_DO_SERVICO`.

Para testar a expiração, reserve outro assento e deixe o checkout aberto por dez minutos. Confira se o pedido passa para EXPIRADO e se o assento volta a ficar livre em uma segunda janela. O workflow também inclui um teste de compra no Chromium com dois usuários observando o mesmo mapa.

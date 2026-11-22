# Estado do desenvolvimento em 21/11/2026

Versão de código `85421e9958336b997d04f36914e5f871d6613d3c`. O relatório do checkpoint documenta o estágio corrente conforme o enunciado. A meta de compra completa registrada em docs/specs/scope-r1.md continua em desenvolvimento.

| Verificação executada | Resultado |
|---|---|
| Java 21 e Maven Wrapper, `./mvnw -o -q verify` | Sete aplicações e duas bibliotecas compiladas. 30 testes unitários passaram, com zero erros, falhas ou testes ignorados. |
| Domínio de pedidos | 25 combinações de estados e dois testes de preços e limites. O relatório JaCoCo registra cobertura de 27/27 linhas no pacote de domínio de pedidos. |
| Identidade e ingressos | Dois testes das decisões sobre organizadores e um da regra de ingresso utilizado. |
| `npm ci --offline --no-audit --no-fund` e `npm run build` em frontend/web | Verificação TypeScript e build de produção Vite concluídos. |
| `docker compose --env-file .env.example config -q` | Configuração aceita pelo Compose. |
| `./mvnw -o -q -Pintegration verify` | Testcontainers iniciou, mas o ambiente não oferecia socket de Docker ativo. As verificações com PostgreSQL, Kafka e Redis não foram concluídas. |

Os testes de integração incluem 500 tentativas sobre um assento, com até 24 sessões simultâneas de banco, reentrega de mensagem e aquisição conjunta de chaves no Redis. O teste de reserva incide sobre o repositório JDBC. A prova pelo endpoint de compra ainda precisa ser implementada. Também permanecem pendentes o fluxo completo no navegador e a confirmação de CI verde no GitHub.

Para repetir o build pela primeira vez em outra máquina, usar Java 21 e `./mvnw verify` com acesso às dependências. Para os testes de integração, iniciar o Docker e rodar `./mvnw -Pintegration verify`. Para subir o ambiente, copiar .env.example para .env, ajustar credenciais locais e executar `docker compose up -d --build`. O catálogo inicia vazio em banco novo.

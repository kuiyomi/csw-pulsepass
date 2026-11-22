# Estado do desenvolvimento em 22/11/2026

O enunciado solicita no Pacote 3 o link para o estágio corrente e a atualização dos Pacotes 1 e 2. O planejamento de R1 no repositório acrescenta uma demonstração de compra completa. O [relatório](Pacote_3_PulsePass.pdf) descreve o código e o alcance das verificações.

| Verificação | Resultado desta revisão |
|---|---|
| TypeScript e Vite | Build de produção aprovado. |
| Java 21, Maven e JaCoCo | Seis serviços e duas bibliotecas verificados. Os 30 testes de domínio passaram, sem falhas, erros ou testes ignorados. |
| Módulo realtime-service | A dependência Spring WebSocket 3.2.5 estava ausente do cache e o Maven Central estava inacessível. Compilação pendente. |
| Docker Compose | Configuração aceita. A sessão de desenvolvimento não tinha acesso ao socket do Docker Desktop para iniciar a pilha. |
| PostgreSQL, Redis e Kafka via Testcontainers | Os três testes da versão anterior passaram em 21/11. A revisão atual aguarda nova execução. |
| Testes HTTP e Chromium | Incluídos no workflow. Aguardam execução com a pilha atual. O teste HTTP exige uma reserva e 499 conflitos. O teste no navegador confere o segundo mapa e o pagamento. |

Os comandos de inicialização, credenciais locais e roteiro de compra estão no [README do projeto](../../README.md). A aprovação remota da CI e o teste da expiração de dez minutos ainda precisam ser confirmados.

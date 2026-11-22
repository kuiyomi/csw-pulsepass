# Estado do desenvolvimento em 22/11/2026

O enunciado solicita no Pacote 3 o link para o estágio corrente e a atualização dos Pacotes 1 e 2. O planejamento de R1 no repositório acrescenta uma demonstração de compra completa. O [relatório](Pacote_3_PulsePass.pdf) descreve o código e o alcance das verificações.

| Verificação | Resultado desta revisão |
|---|---|
| TypeScript e Vite | Build de produção aprovado. |
| Java 21 e Maven | BUILD SUCCESS em 20,180 s, incluindo o serviço de tempo real. Passaram 27 testes do domínio de pedidos, dois de decisão sobre organizadores e um de uso do ingresso. |
| Docker Compose | Configuração aceita. |
| Integração com Testcontainers em 22/11 | Três testes aprovados. O PostgreSQL permitiu uma reserva em 500 tentativas concorrentes no repositório JDBC. No Redis, uma seleção com conflito não deixou bloqueios parciais. O teste com Kafka e PostgreSQL verificou a republicação e o efeito único no consumo de uma duplicata. |
| Total da suíte Maven | 33 testes, zero falhas, zero erros e zero ignorados nos seis relatórios XML do Surefire. |
| Compra via HTTP | Após o ajuste no cliente de teste, o roteiro passou no Mac. Validou login, permissões, publicação, reserva atômica, recusa, timeout, aprovação e repetição de chaves. Das 500 requisições sobre o mesmo lugar, uma obteve HTTP 201 e 499 receberam HTTP 409. |
| Compra em Chromium e segundo mapa | Roteiro incluído na CI. Resultado não aferido nos relatórios locais. |

Os comandos de inicialização, credenciais locais e roteiro de compra estão no [README do projeto](../../README.md). A aprovação remota da CI e o teste da expiração de dez minutos ainda precisam ser confirmados.

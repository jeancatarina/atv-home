# Social — estado da implementação

Atualizado em 2026-11-09. O plano está em [PLANO.md](PLANO.md); a revisão que trouxe a progressão, o visual novo e a variedade está nas seções abaixo. Aqui está o que existe, com a prova, e o que só pode ser provado com pessoas.

**Resumo:** 14 jogos, sala, protocolo, página do celular, cerimônia, progressão (XP, níveis, conquistas, temporadas, recordes, desafios, rivalidades) e telas da TV no padrão Apple TV. A lógica e o celular foram provados sem a TV; a TV foi instalada e jogada de verdade (uma partida completa com cinco celulares de teste). Faltam o som real, a legibilidade a 3 m e o playtest com gente.

## O que mudou depois da primeira noite de jogos (feedback real)

| Feedback | O que foi feito |
|---|---|
| Entrar num jogo e voltar travava o controle | O foco voltava para um botão que não existia mais. Agora ele volta ao fechar a página de jogo, o perfil, o convite e o menu de pausa (e a aba atual guarda o foco) |
| A foto não podia ser colocada depois | "Trocar minha foto" no lobby e no perfil do celular, a qualquer momento |
| A foto devia ser obrigatória | Sem selfie não entra (um aparelho que já mandou uma pode pular). A TV mostra o rosto de cada pessoa |
| O jogo dos agentes e assassinos (Dupla Secreta) não agradou | Removido por inteiro (jogo, tela, conteúdo, recorde e testes) |
| Pouca variedade e conteúdo repetido | Veja "Variedade" abaixo |
| A história do Mistério era impossível | As 9 histórias foram trocadas por 20 de lógica dedutível, no estilo dos quebra-cabeças clássicos de raciocínio |
| Filme em 5 Pistas foi o melhor, mas faltou variedade | A cada partida busca outros filmes e acumula até 120, e os já jogados saem do sorteio |

## Variedade

- **Memória do que a casa já jogou** (`Ledger.seen`, no mesmo `ledger.json`): o pacote de cada jogo chega ao jogo só com o que ainda não saiu. Quando sobra pouco, recomeça, então nunca fica sem conteúdo. `VarietyTest`: duas partidas seguidas nunca repetem pergunta, a memória sobrevive a reiniciar, o pacote recomeça sem travar.
- **Memória do que cada pessoa jogou** (`Career.lastByGame` e `played:<jogo>`): o destaque da Home sugere o jogo que a mesa menos jogou ("3 de 5 nunca jogaram", "Novidade para todo mundo aqui"), nunca o que acabou de jogar, e o "Surpreenda-me" sorteia entre as cinco melhores sugestões.
- **Conteúdo ampliado:** Quem é Mais Provável 120 → 246 perguntas; Sintonia de Casal 50 → 130; Frequência 50 → 123; Quem Sabia? 60 → 242 palavras; Missões 63 → 116; Duas Verdades 30 → 82; Psicólogo 24 → 58; Lugar Secreto 24 → 44; Tribunal 4 → 14 casos (os álibis ainda são sorteados). Mistério foi para 20 histórias melhores.

## Match sem fim e linguagem mais humana (2026-11-10)

- **Match infinito:** o baralho não acaba mais. Quando quem está mais adiantado chega perto do fim, a TV busca outro lote e os celulares recebem as cartas novas sozinhos ("Buscando mais opções pra vocês…"). Acabou o "mais votado" com uma pessoa só: só vale o que todo mundo curtir. `MatchRulesTest` (a fila cresce até todos concordarem), `MatchServerContractTest` (`/deck?from=`, cartas repetidas ignoradas), `tools/match-phone-endless.js` (celular em Chrome contra uma TV falsa).
- **Memória do Match** (`MatchMemory`, `match_memory.json`): o que a casa recusou vai para o fim da fila e só volta depois de 10 dias; o que já deu match também não volta logo. A ordem é: nunca recusados, recusados há mais tempo, recusados há pouco. `MatchMemoryTest`.
- **Linguagem:** revisei os textos do Social para soar como gente falando (pra, tá, "Olha pra TV", "Acabou a partida", "Não rolou") e não como manual: falas do apresentador, troféus, regras de cada jogo, mensagens do celular e da TV. Tirei palavras com gênero (ex.: "Ele acertou?" virou "Acertou a regra?").
- Os testes de interface (`tools/*.js`) foram atualizados com os textos novos e passam: `social-phone-premium.js`, `social-phone-e2e.js`, `social-phone-fuzz.js`.
- Ainda não foi para a TV: tudo desta rodada foi provado só localmente.

## Provado sem a TV

| O quê | Prova |
|---|---|
| Servidor (`LanServer`), Match, sala, protocolo, segurança | `LanServerTest`, `MatchServerContractTest`, `RoomTest`, `RoomServerTest` (inclui o rosto de quem está na sala para o pôster) |
| Contrato de **todos os 14 jogos** com bots, saídas, relógio e segredos | `GameContractTest` |
| Regras de cada jogo, Bomba (600 bombas desarmáveis), Tribunal (1.500 casos), Filme | `GamesTest`, `MoreGamesTest`, `Film5Test`, `BombTest` |
| Progressão: XP, níveis, conquistas uma vez só, temporada que vira, recordes, desafio semanal, migração do arquivo antigo | `ProgressionTest`, `LedgerTest` |
| Variedade e sugestão | `VarietyTest` |
| Falas do apresentador sem repetição nem pontuação dobrada ("Provável?.") | `NightTest` |
| Telas da TV a 1920×1080 (Roborazzi + Robolectric): Home, página do jogo, palco de cada jogo, resultado, ranking, perfis, ajustes, celebrações | `app/src/test/.../social/screens`, imagens em `app/build/shots/` |
| Celular em Chrome real: entrar (com selfie obrigatória), cartão de nível, perfil, momentos com confete, envelope com lacre, 14 jogos, reconexão, pausa, noite, outro dia e pôster com rostos | `tools/social-phone-e2e.js`, `social-phone-fuzz.js`, `social-phone-premium.js` |
| Na TV real, com 5 celulares de teste: entrar, votar, segurar OK para revelar, resultado, subida de nível, ranking | `tools/social-tv-open.sh` e `tools/social-tv-bots.js` |

## Os 14 jogos

Quem é Mais Provável, Infiltrados, Frequência, Sintonia de Casal, Duas Verdades e Uma Mentira, Lugar Secreto, Quem Sabia, Psicólogo, Mistério Sim ou Não, Missões Secretas, Máfia de Uma Noite, Tribunal dos Amigos, Filme em 5 Pistas e Desarme a Bomba.

## Ainda de fora

- Assinaturas dos jogos: Aposta no Quem é Mais Provável (o "Se defende!" na revelação já existe), modo Camaleão, Jornal da Vila na Máfia, câmera lenta e linha do tempo na Bomba, histórico do casal.
- Mais conteúdo: os pacotes maiores agora duram dezenas de partidas; o Mistério (20) e o Tribunal (14 casos base) são os mais curtos.

## Só com pessoas e com a TV

- [ ] Ouvir os sons (sintetizados; nunca tocaram).
- [ ] Legibilidade a 3 m, controle remoto em todos os jogos (setas no dial e na bomba), desempenho com 12 celulares (`tools/social-perf.sh`).
- [ ] Confirmar que o foco volta depois de pausar e de fechar a página de um jogo (corrigido; provado nos testes e numa partida de teste).
- [ ] Playtest: diversão de cada jogo, tom do apresentador, conteúdo.
- [ ] iOS Safari, Samsung Internet e Firefox Android (só Chrome foi testado).

## Como rodar

```sh
export JAVA_HOME=...  # um JDK 17
./gradlew :social-core:test :app:testDebugUnitTest                         # tudo na JVM
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true              # regrava as capturas da TV
BOT_MS=700 tools/social-devroom.sh                                          # sala de verdade, sem TV
PLAYWRIGHT=/caminho/node_modules/playwright node tools/social-phone-fuzz.js
KEY=$(tools/social-tv-open.sh) TV_URL=http://IP-DA-TV:8766 node tools/social-tv-bots.js   # celulares de teste na TV
```

## Fechamento da rodada (2026-11-10)

- **Cerimônia** no visual novo: holofote, o troféu entra com mola, coroa no destaque da noite ("O grande final"), pontinhos de progresso e, no fim, a estante de troféus em cartões com rosto e nível.
- **Menu de pausa** em cartão arredondado, com "Continuar" em destaque.
- **Revelação do Quem é Mais Provável** chama quem levou mais votos para se defender ("Se defende!").
- Capturas novas: `ceremony-trophy`, `ceremony-champion`, `ceremony-shelf`, `pause`. Testes (`:social-core`, `:app`), scripts de navegador e o APK de release passam.

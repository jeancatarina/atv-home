# Plano técnico e de produto — ATV Home · Social

> **Atualização de 2026-11-09 (depois da primeira noite de jogos):** a **Dupla Secreta (Codenames: Duet) foi removida** do catálogo a pedido de quem jogou, então este plano tem 14 jogos, não 15. A seção 5.11 e a campanha ficam só como histórico. Entraram a memória do que cada casa e cada pessoa já jogou (variedade) e a foto obrigatória; veja [STATUS.md](STATUS.md).


> Repositório analisado: `jeancatarina/atv-home` no commit `bb1af63`. A cópia local `/Users/jean/dev/atv-home` é igual ao `origin/HEAD` (conferido com `git ls-remote`). As medições foram feitas na BRAVIA por `adb`, só com leitura, em 2026-11-09. Nenhum arquivo foi alterado.
>
> Legenda: **[C]** confirmado no código ou medido · **[P]** proposta deste plano.

## Contexto

O ATV Home ("Início") é um launcher de Android TV em Kotlin + Compose for TV, feito para uma Sony BRAVIA (Android 10, 2,3 GB). Ele já tem um recurso multiplayer local, o **Match**: a TV serve uma página na rede de casa, os celulares entram pelo QR code e votam em filmes. O objetivo agora é uma área **Social** com 15 jogos de grupo. A TV é o palco e o dono do estado. Os celulares mostram só o que é privado e recebem decisões. A área precisa reaproveitar a infraestrutura do Match sem mudar o comportamento dele, não pode custar nada ao launcher quando não está em uso e precisa caber no hardware, que é limitado e já fica ocupado.

---

## 0. Visão de produto: a noite como um programa de TV [P]

### 0.1 Tese

**Uma noite de jogos no Social é um programa de TV em que vocês são o elenco.** A TV é o palco e o apresentador: ela conhece o rosto de cada um, lembra o que aconteceu na noite, comenta com humor e fecha a noite com uma cerimônia de troféus. O celular é um **envelope lacrado**: você segura para abrir o seu segredo e toca uma vez para decidir. Todo o resto acontece olhando para a TV e para as pessoas.

### 0.2 Uma noite com o Social (como deve parecer)

1. **20:30** — Jean aperta **Social**. A TV abre um palco escuro, com um holofote sobre o QR code. Ana, Bia, Caio e Dani escaneiam, e cada um tira uma **selfie em dois toques**. Os rostos entram no "chão" do palco, um a um, com um *plim*. O Apresentador diz: *"Casa cheia: 5 pessoas. Que comecem os jogos."*
2. Alguém aperta **Surpreenda-me**. Três rolos de caça-níquel giram com os cartões dos jogos (a mesma mecânica da Roleta de filmes) e param em **Infiltrados**. Vem uma vinheta de 2 s com nome e som, e uma cortina se abre.
3. Os celulares vibram de leve e mostram um **envelope**. Cada um segura para ver a própria palavra e solta. Na TV, a vez de falar passa de rosto em rosto.
4. Na votação, as fichas dos votos **voam até o rosto** de Caio. O cartão dele **gira**, o carimbo **INFILTRADO** cai com um tremor e o Apresentador provoca: *"Caio, cara de pau nível profissional."* Caio ganha **10 s de holofote** para a última palavra.
5. **"Mais uma"**: papéis novos e ninguém precisa reconectar. Depois, **"Trocar de jogo"**: Quem é Mais Provável, Frequência e Desarme a Bomba, com o controle passando de mão em mão.
6. **23:10** — **"Encerrar a noite"**: a **Cerimônia** entrega um troféu por vez, com rosto, número e som (*Cara de Pau*, *Sherlock*, *Nervos de Aço*, *Alvo Preferido*). Todo mundo ganha pelo menos um. Cada celular recebe o próprio troféu e o **pôster da noite** para baixar e mandar no grupo.

### 0.3 Cinco pilares da diversão

| Pilar | O que significa | Como aparece |
|---|---|---|
| **1. Vocês são o elenco** | O jogo é sobre as pessoas da sala, não sobre fichas genéricas | Rosto por selfie (ou um rabisco, ou a inicial). Os rostos estão em tudo: cartão que vira, "Procurado", ficha criminal, manchete do jornal, troféus |
| **2. A TV tem personalidade** | Um apresentador com timing e memória da noite | Falas curtas, contextuais e com dados da noite (*"Bruno, acusado pela 3ª vez hoje. Coincidência?"*). Tom **Família** ou **Provocador**. Texto com som. A voz (TTS) é opcional |
| **3. Toda partida tem um clímax** | Revelações coreografadas, nunca um "resultado: X" seco | 6 coreografias reutilizáveis sincronizadas com som: cartão que vira, holofote, carimbo, fichas voando, manchete, explosão de confete (seção 0.5) |
| **4. Ninguém fica parado** | Eliminado, espectador ou quem espera sempre tem algo leve para fazer | **Holofote** (10 s para se defender ou dar a última palavra), **palpite fantasma**, **aposta da plateia**, **carimbos de reação** que sobem na TV (*Suspeito!*, *Mentira!*, *Kkkk*) |
| **5. A noite tem história** | Os jogos somam numa narrativa da noite e da casa | **Placar da noite** (fichas ★ entre jogos), **Cerimônia** com troféus, **pôster da noite**, **recordes da casa**, **Bomba do dia**, **Campanha da Dupla**, **histórico do casal** |

### 0.4 A camada da noite

- **Fichas ★:** cada jogo converte o resultado em fichas: vitória +3, destaque +1 a +2 (MVP, "na mosca", desarme), aposta certa +1. O **Placar da noite** fica na coluna da Sala e não depende do jogo.
- **Destaques:** ao terminar, cada jogo emite fatos tipados (`Highlight`): `FOOLED(n)`, `CAUGHT(spy)`, `UNANIMOUS(target)`, `BULLSEYE`, `DEFUSED(seconds)`, `SYNC(pct)`… Eles alimentam as fichas, o Apresentador e a Cerimônia. É uma lista simples, sem framework.
- **Apresentador:** um pacote de ~400 falas-modelo com variáveis e condições sobre os destaques da noite (*"{nome} foi o alvo {n} vezes"*). Tem tempo mínimo entre falas, não repete fala na mesma noite e **nunca fala em fase de segredo ou de concentração**. A escolha é uma função pura, testável e com semente.
- **Cerimônia:** em "Encerrar a noite", ou ao sair do Social depois de pelo menos 2 jogos. São ~20 troféus calculados dos destaques, com a regra **"todo mundo ganha pelo menos um"** (distribuição gulosa, com *"Presença de Palco"* como reserva). Vêm um por vez, com rosto, número e som. No fim, o celular recebe **"Seu troféu"** e **"Baixar o pôster da noite"**, um PNG desenhado **no próprio celular** (canvas, sem custo para a TV) com rostos, troféus e placar.
- **Surpreenda-me:** caça-níquel de jogos que reusa a mecânica de rolos da Roleta (`Roulette.kt:84-92`). Sorteia entre os jogos que servem para o número de pessoas e que ainda não foram jogados na noite.
- **Memória da casa** (opcional, no máximo 50 perfis): o perfil é ligado ao aparelho (`deviceId` no `localStorage`) e guarda nome, rosto (só se a pessoa marcar **"Lembrar meu rosto nesta TV"**) e estatísticas. Isso alimenta os **Recordes da casa** (fileira no Social), o **histórico do casal** (Sintonia), a **Campanha da Dupla** e a **Bomba do dia**.

### 0.5 Sistema "Palco": acabamento visual e sonoro

Ele estende os tokens `Tv.*` e o verde da casa (`Accent.Wall`). Nenhuma peça nova pode quebrar as regras de GPU da seção 6.

- **Tipografia de palco:** `Stage.Hero` 72 sp (palavra secreta, tempo, troféu), `Stage.Title` 44 sp, `Stage.Body` 24 sp. **Nada que precise ser lido durante o jogo fica abaixo de 20 sp**, para ler a 3 m.
- **Cor:** o verde da casa é a base. Cada jogo tem **uma cor de destaque** e um **glifo** (15 cores harmonizadas e testadas em contraste). As cores semânticas são fixas: acerto verde, erro vermelho, **segredo dourado**.
- **Avatar:** rosto em círculo + anel na cor do jogador + **forma** no canto (● ▲ ■ ◆ ★ ✚), para quem tem daltonismo. Os estados (pronto, votou, offline, eliminado, falando) são marcas pequenas, nunca animação contínua.
- **Movimento:** 4 durações (120/240/480/900 ms), 2 curvas (saída enfática e spring suave) e **6 coreografias** escritas uma vez e usadas por todos os jogos:
  - `Flip`: o cartão gira 180° e mostra rosto e papel.
  - `Spotlight`: um círculo de luz assado em bitmap varre os avatares e para.
  - `Stamp`: carimbo cai com escala de 1,4 para 1, mais 2 frames de tremor.
  - `Tally`: fichas pequenas voam e o contador soma.
  - `Headline`: manchete de jornal gira e para.
  - `Burst`: até 40 partículas por 1,2 s.

  Cada uma é medida na TV uma vez e tem um som sincronizado.
- **Som:** um kit de **14 sons semânticos** (entrar, travar, abrir envelope, revelar, acerto, erro, virada, tique, alerta, vitória, derrota, fanfarra, suspense, carimbo), curtos, mono e nivelados. Opcionalmente, **1 ambiente por jogo** (chuva noir no Mistério, grilos na noite da Máfia, tique na Bomba), em loop, desligável. Música e sons são **próprios ou CC0**.
- **Transições:** **cortina** (2 painéis sólidos, 450 ms) entre a sala e o jogo, e **vinheta de abertura** de 2 s por jogo (glifo + nome + som), como abertura de programa.
- **Ilustração:** um estilo único (vetores planos, 2–3 cores + verde, traço grosso) em VectorDrawable: nítido em qualquer tamanho, poucos KB. As regras ficam no guia `docs/social/ARTE.md`.
- **Primeira vez:** nos jogos complexos (Máfia, Tribunal, Bomba), a primeira partida da casa é uma **rodada de treino guiada**, em vez de texto de regras.

### 0.6 O celular como envelope lacrado

- **Entrar:** o nome vem preenchido, a selfie sai em 2 toques (`<input capture="user">` funciona em HTTP, ao contrário da câmera ao vivo) ou dá para fazer um **rabisco** com o dedo. A foto é reduzida no próprio celular para 192×192 JPEG (~15 KB).
- **O envelope:** o segredo aparece como um envelope com lacre. **Segurar abre** (com vibração no Android) e soltar fecha. A estrutura e o tempo são iguais para todos os papéis.
- **Decidir:** alvos de toque grandes (≥ 56 px), estado **"travado ✓"** satisfatório e "mudar voto" até o prazo.
- **Esperar:** "Olhe para a TV" com os **carimbos de reação** opcionais (no máximo 1 a cada 2 s, só nas fases de conversa).
- **Fim:** "Seu troféu" e o pôster da noite. **O celular nunca toca som** (denunciaria o papel).
- **Visual:** fundo preto, verde da casa, fonte do sistema (SF no iPhone, como o launcher imita) e `prefers-reduced-motion` respeitado.

### 0.7 Telas-chave (esboços)

```
(A) Sala vazia                                       (B) Social com gente
┌────────────────────────────────────────────┐      ┌───────────────────────────────┬─────────────┐
│ Social                               21:04 │      │ Social   "Casa cheia: 5." ◀ Apresentador     │
│               ┌──────────┐                  │      │ [Surpreenda-me]  [Placar ★]   │ SALA  ▣ QR  │
│     holofote  │ ▓▓ QR ▓▓ │  Aponte a câmera │      │ Para 5 agora                  │ (A)(B)(C)   │
│               └──────────┘  192.168.1.3:8766│      │ ▢Infiltrados ▢Lugar ▢Frequênc │ (D)(J)      │
│  ○ ○ ○ ○  ← os rostos entram aqui ("plim")  │      │ Dedução e blefe  ▢ ▢ ▢ ▢      │ ★ Ana 7     │
└────────────────────────────────────────────┘      │ Recordes da casa ▢ ▢           │ ★ Caio 5    │
                                                     └───────────────────────────────┴─────────────┘
(C) Palco — votação                                  (D) Revelação
┌────────────────────────────────────────────┐      ┌────────────────────────────────────────────┐
│ INFILTRADOS · Rodada 2 · Votação    ◔ 0:31 │      │            ┌───────────┐                    │
│          Quem é o infiltrado?               │      │            │  (rosto)  │ ← Flip + Stamp     │
│   (A)✓  (B)✓  (C)…  (D)✓  (J)…   3 de 5     │      │            │ INFILTRADO│   Caio · Piscina   │
│   [Suspeito!]·Bia  ← carimbos sobem e somem │      │            └───────────┘                    │
└────────────────────────────────────────────┘      │ "Caio, cara de pau nível profissional."    │
                                                     └────────────────────────────────────────────┘
(E) Celular                                          (F) Cerimônia
┌───────────┐ ┌───────────┐ ┌───────────┐            ┌────────────────────────────────────────────┐
│ Ana ●     │ │ Rodada 2  │ │Olhe p/ TV │            │ CERIMÔNIA DA NOITE · 9 de novembro          │
│ ┌───────┐ │ │ (B) Bia   │ │[Suspeito!]│            │              TROFÉU CARA DE PAU            │
│ │ LACRE │ │ │ (C) Caio ✓│ │[Mentira!] │            │        (rosto da Bia) enganou 7 pessoas    │
│ └───────┘ │ │ (D) Dani  │ │[Kkkk]     │            │        próximo troféu em 4 s…              │
│segure p/ abrir│ travado ✓ │ │           │            └────────────────────────────────────────────┘
└───────────┘ └───────────┘ └───────────┘
```

### 0.8 Diversão medida, não presumida

Assim como o desempenho, a diversão tem metas e um **portão** antes de cada jogo entrar no catálogo:

- **Telemetria local** (build de desenvolvimento, sem rede): o `NightLog` registra a duração de cada fase, quantos jogadores têm ação pendente, o tempo até cada decisão e o tempo com o envelope aberto. O resultado é um log `Social.fun` e um relatório por sessão.
- **Metas por jogo:** explicação de regras ≤ 45 s · **primeira decisão ≤ 60 s** depois de "Começar" · **tempo morto** (ninguém tem o que fazer e nada acontece na TV) com p90 ≤ 15 s · **atenção no celular ≤ 20%** do tempo da rodada (exceto Missões e o especialista da Bomba) · nenhuma dúvida de regra que trave a mesa por mais de 30 s.
- **Playtest obrigatório:** 2 sessões (casal e grupo) com uma ficha curta: houve um pedido espontâneo de "mais uma"? O momento uau provocou reação audível? Nota ≥ 4/5 em "quero jogar de novo"? O que travou ou entediou?
- **Ficha de design de 1 página por jogo**, aprovada **antes** do código: tese, momento uau, primeiro minuto, mapa de tempo morto, virada (como quem está atrás ainda pode ganhar) e como termina numa risada.
- **Polimento é escopo:** cada fase reserva cerca de 25% do esforço para som, movimento, textos e ajustes de playtest.

---

## 1. Diagnóstico do repositório atual

### 1.1 O que existe [C]

| Área | Arquivos | O que faz hoje | Uso no Social |
|---|---|---|---|
| Aplicação | `App.kt` | Singletons manuais (`catalog`, `apps`, `myList`, `deepLinks`). Coil com cache de memória de 64 MB e de disco de 200 MB (`App.kt:45-50`) | Usar o Coil como está. **Não** inicializar nada do Social aqui |
| Navegação | `HomeActivity.kt` | Pilha `mutableStateListOf<Screen>` (`:47-60`). A Home fica composta por baixo com alpha 0 (`:86-110`). **Só o topo da pilha é composto** (`:111-123`). VOLTAR desempilha (`:78-80`), HOME limpa a pilha (`:156-163`) e MENU abre a Central (`:142-145`) | Criar `Screen.Social`. O ciclo de vida da sala precisa ficar fora da composição (ver 1.2) |
| Servidor do Match | `data/MatchServer.kt` | NanoHTTPD na porta fixa 8765 (`:125`). Rotas `/ /deck /join /vote /state` (`:48-63`), métodos `@Synchronized` (`:69-87`), `StateFlow<MatchState>` para a TV (`:43-44`), corpo lido por `parseBody` (`:110-115`), página lida dos assets a cada GET (`:121`) e `localIp()` (`:128-134`) | Extrair a parte genérica para `LanServer` sem mudar rotas nem JSON |
| Regras do Match | `data/MatchRules.kt`, `test/.../MatchRulesTest.kt` | Kotlin puro e testado. IDs sequenciais `p1, p2…` (`:22-26`) | Padrão a repetir: regras puras + servidor fino + tela |
| Tela do Match | `ui/discover/Match.kt` | Servidor criado num `LaunchedEffect(type)` e parado no `DisposableEffect` (`:87-110`). QR do ZXing de 512×512 ARGB_8888, cerca de 1 MB (`:229-234`). Revelação com spring (`:183-227`) | Extrair `QrCode` numa versão compacta. A revelação inspira a coreografia `Stamp` |
| Roleta | `ui/discover/Roulette.kt` | Rolos de caça-níquel com intervalo crescente de 55 a 230 ms (`:84-92`) e `Reel` sem crossfade (`:143-155`) | Base do **Surpreenda-me** |
| Celular do Match | `assets/match.html` (163 linhas) | HTML e JS puros. Nome guardado no `localStorage` (`:72`), polling de `/state` a cada 2 s (`:82`), `esc()` contra injeção de HTML (`:160`) e vibração na revelação (`:156`) | Base de estilo para o celular. O transporte será outro |
| Sistema de design | `ui/theme/Theme.kt`, `Accent.kt`, `ui/components/Components.kt`, `TvScroll.kt` | Fonte Inter, tokens `Tv.*`, tela de 960×540 dp (`Theme.kt:17`), verde da casa com `screen()` e `pill()`, `FocusCard` (zoom, inclinação e brilho), `PillButton`, `Chip`, `ContentCard`, `PosterCard`, `RowTitle`, `FocusMemory`, `LastKey` | Usar tudo como está. O sistema "Palco" (0.5) se apoia nele |
| Lições de desempenho escritas no código | `Components.kt:119`, `Trailer.kt:49-50`, `Scrim.kt:11-15`, `HomeScreen.kt:213, 623` | Sombra borrada custava ~15 ms por frame. WebView invisível custa ~6 ms de GPU por frame. Degradês de tela cheia são assados no bitmap. Uma animação infinita por cartão mantinha a tela redesenhando. A Home não pinta fundo de tela cheia | Viram regras do orçamento de GPU do Social (seção 6) |
| Persistência | `MyList.kt:21-38`, `Apps.kt:28`, `Weather.kt:48` | Arquivo JSON com gravação atômica (tmp + rename) e SharedPreferences | Mesmo padrão para `room.json`, perfis e recordes |
| Catálogo | `data/Catalog.kt`, `Models.kt`, `HomeViewModel.kt:98-232` | `discover`, `details` (elenco, tagline, gêneros, backdrop, logo, providers), `LruCache(300)`, `Semaphore(6)`, `myProvidersOf`, `packageFor`, `watch`, `prefetch` | Base do **Filme em 5 Pistas** e do "Assistir agora" no fim do jogo |
| Protetor de tela | o da Sony (Decoração do Ambiente) + `saver/WeatherStrip.kt` | DreamService de outro app | Precisa ser segurado durante o jogo (1.2, item 8) |
| Testes | `app/src/test/...` (5 classes) | Só JUnit 4 na JVM, só lógica pura. Sem Robolectric, sem testes de UI ou instrumentados, sem CI no repositório | Os testes do Social seguem esse estilo e acrescentam testes de protocolo com sockets reais |
| Build | `app/build.gradle.kts`, `gradle/libs.versions.toml` | Um só módulo `:app`. Kotlin 2.1.0, Compose BOM 2024.12.01, tv-material 1.0.0, NanoHTTPD 2.3.1, ZXing 3.5.4, OkHttp 4.12, kotlinx.serialization 1.7.3. minSdk 26, release com R8 (`proguard-rules.pro` mantém `fi.iki.elonen.**`) | Acrescentar um módulo JVM `:social-core` (decisão D4) |
| Convenções | — | Textos de interface em pt-BR direto no código, README em pt-BR, commits em inglês no imperativo. Não há AGENTS.md, CLAUDE.md nem `.github` | Seguir as mesmas convenções |

### 1.2 Limitações confirmadas que o Social precisa resolver [C]

1. **O ciclo de vida do servidor está preso à tela.** Como só o topo da pilha é composto (`HomeActivity.kt:113-121`), abrir "Detalhes" na revelação do Match tira o `MatchScreen` da composição. O `DisposableEffect` então para o servidor (`Match.kt:110`), os celulares perdem a sessão e, ao voltar, um baralho novo é montado.
2. **A identidade dá para adivinhar.** Os IDs são `p1, p2…`, sem token (`MatchRules.kt:23`): qualquer aparelho na rede pode votar no lugar de outro.
3. **A sincronização é um polling de 2 s** (`match.html:82`): até 2 s de atraso, e cada consulta é uma requisição nova.
4. **Não há reconexão.** Recarregar a página perde o `me` (`match.html:68-83`).
5. **Não há teto de corpo nem de threads.** `parseBody` aceita qualquer tamanho. Pelo que se sabe da NanoHTTPD 2.3.1, corpos acima de 1 KB vão para um arquivo temporário e o `DefaultAsyncRunner` abre uma thread por conexão, sem limite. Conferir no código da biblioteca na Fase 0.
6. **A porta é fixa (8765) e não há alternativa** se ela estiver ocupada (`MatchServer.kt:125`).
7. **Cada QR gerado é um bitmap de cerca de 1 MB** (`Match.kt:230-233`).
8. **O protetor de tela entra no meio do jogo.** Na TV o protetor está ligado e `screen_off_timeout` vale 300000 ms. Não existe nenhum `keepScreenOn` no app (`grep` sem resultados). Num jogo controlado pelos celulares o controle remoto fica parado, e o protetor entraria aos 5 minutos.

### 1.3 Linha de base medida na BRAVIA [C]

Uma única amostra, sem roteiro controlado. Na Fase 0 ela é repetida com um protocolo fixo.

| Item | Valor |
|---|---|
| Aparelho | BRAVIA_4K_UR2, Android 10, SoC `mt5893`, 4× Cortex-A73, ABI `armeabi-v7a` (processo de 32 bits) |
| Memória | MemTotal 2.361.464 kB, MemAvailable ~658 MB, `heapgrowthlimit` 192m |
| Tela | 1920×1080 com densidade 320, ou seja 960×540 dp |
| Launcher na Home (sem WebView ativa) | **PSS 123,2 MB**: Java 25,8 · Native 50,4 · Graphics 19,0 · Code 4,1 · Other 13,3 · System 10,5. RSS 212 MB, **66 threads**, APK de 2,52 MB |
| Frames desde que o processo abriu (inclui trailers) | 2.366 frames, **26,6% com jank**, p50 11 ms, **p90 26 ms**, p95 42 ms, p99 150 ms |
| CPU | O launcher fica em 0% parado. **A TV inteira está ~66% ocupada (load average ~33)**: sobra pouca CPU |

### 1.4 Resumo de reuso

- **Reusar direto:** `Tv.*`, `Accent`, `FocusCard`, `PillButton`, `Chip`, `PosterCard`, `ContentCard`, `RowTitle`, `FocusMemory`, `TvScroll`, Coil, `Catalog`, `TmdbClient.image`, `HomeViewModel.watch/myProvidersOf/prefetch`, o padrão de JSON atômico do `MyList`, a mecânica de rolos da Roleta e o estilo visual do `match.html`.
- **Extrair sem mudar o comportamento:** `LanServer` (vem do `MatchServer`), `QrCode` (vem do `Match.kt`) e `Reel` (vem do `Roulette.kt`; a Roleta continua igual).
- **Não reusar:** o polling de 2 s, os IDs sequenciais e o ciclo de vida no composable. O Match continua com eles.

---

## 2. Arquitetura proposta [P]

### 2.1 Visão geral

```
 Celulares (navegador, sem instalar nada)          TV — processo do launcher
 ┌──────────────────────────┐   HTTP na rede :8766  ┌─────────────────────────────────────────────┐
 │ social/web/index.html    │ ─ POST /api/join ───▶ │ RoomServer (LanServer · NanoHTTPD)          │
 │ app.js = casca + widgets │ ─ POST /api/face ───▶ │   teto de corpo · Host · token · dedupe     │
 │ + envelope + pôster      │ ─ POST /api/act  ───▶ │ Room (autoritativo, um lock)                │
 │ localStorage: token, id  │ ◀ GET /api/view ───── │   lugares · rostos · presença · prazos      │
 │ (long-poll de 25 s)      │   ?since=<hash>       │   reações · apostas · pausa                 │
 └──────────────────────────┘                       │   game: Game<S> (regras puras, imutáveis)   │
                                                    │   Night: fichas · destaques · apresentador  │
                                                    │   snapshot: StateFlow ──▶ Compose (palco)   │
                                                    └─────────────────────────────────────────────┘
        SocialViewModel (criado sob demanda) abre a Room ao entrar no Social e fecha quando nenhuma tela
        Social resta na pilha (com 60 s de tolerância). O Match segue no próprio servidor :8765.
```

### 2.2 Decisões, com comparação

**D1 — Transporte: long-poll sobre a NanoHTTPD que já existe.**

| Critério | Polling 2 s (Match) | **Long-poll** | SSE | WebSocket |
|---|---|---|---|---|
| Latência da TV ao celular | até 2 s | 1 ida e volta (~10–50 ms na rede local) | 1 ida e volta | 1 ida e volta |
| Dependência nova | nenhuma | **nenhuma** | nenhuma (`ChunkedResponse`) | `nanohttpd-websocket` 2.3.1, de 2016 |
| Threads na TV | 1 por pedido curto | 1 por celular esperando | 1 por celular + batimento | 1 por celular + ping/pong |
| Reconexão e presença | triviais | **triviais**: cada pedido é independente e leva `since`; a presença sai do último pedido | o EventSource reconecta sozinho, mas detectar conexão morta exige batimento | manual no JS |
| Celular bloqueado (iOS) | funciona | funciona | a conexão cai e volta | cai e precisa ser reaberta à mão |
| Complexidade | baixa | **baixa** | média | média a alta |

Nenhum jogo precisa de envio contínuo do celular para a TV, porque o dial, a grade e a bomba usam o controle remoto (seção 5). O contrato é por **foto completa** (cada resposta traz a visão inteira do jogador), então eventos duplicados, atrasados ou fora de ordem não causam dano. Se as medições reprovarem, dá para trocar para SSE sem mexer na `Room`.

**D2 — Celular: interface descrita pela TV, com cerca de 10 widgets.** A TV manda um `PhoneView` (dados) e o `app.js` desenha. A maioria dos jogos precisa de **0 linhas** de JS próprio, o teste de vazamento de segredo roda na JVM e o acabamento (envelope, "travado ✓", reações) é feito **uma vez** e vale para todos. Regra: um widget novo só entra quando dois jogos ou mais precisarem dele.

**D3 — Dono da sala: um `SocialViewModel` com escopo da Activity, criado sob demanda.** Custa zero quando não é usado (`by viewModels()` é preguiçoso) e sobrevive à pilha ("Detalhes", "Assistir"). A sala vive enquanto houver uma tela Social na pilha e espera **60 s** antes de fechar. Quando o launcher perde o foco, o jogo pausa e a sala fecha depois de 15 min.

**D4 — Um módulo Gradle JVM `:social-core`, sem Android.** Nele ficam as regras, a sala, o protocolo, a camada da noite (fichas, apresentador, cerimônia) e o `LanServer`. O **compilador** garante que as regras não importam Compose nem Android, e os testes de protocolo rodam com sockets reais na JVM. O custo é um `build.gradle.kts` e uma entrada no catálogo de versões.

**D5 — Concorrência: um lock na `Room` e estados imutáveis.** As ações levam microssegundos. O long-poll espera num `StateFlow<Long>` de versão com `withTimeoutOrNull`.

**D6 — Cronômetros: prazos absolutos no relógio da TV.** O celular calcula a diferença de relógio e desenha o tempo localmente. Ninguém manda "tique" pela rede, e uma única corrotina agenda o próximo prazo. A pausa desloca os prazos.

**D7 — Desenho na TV: estado imutável dividido em fatias, com animação na fase de desenho.** As 6 coreografias de 0.5 são composables com animações finitas que leem os valores em `graphicsLayer` e `drawWithContent`.

**D8 — Persistência mínima:** `room.json` (chave da sala + lugares, válido por 12 h), `profiles.json` (até 50 perfis, só com consentimento para o rosto), `records.json` (recordes, campanha, Bomba do dia) e `seen.json` (conteúdo sem repetir). **O estado do jogo não é salvo.**

### 2.3 Componentes e responsabilidades [P]

| Componente | Onde | Responsabilidade | Fica de fora |
|---|---|---|---|
| `LanServer` | `:social-core/net` | NanoHTTPD, roteamento, teto de corpo por rota (4 KB, ou 32 KB no rosto), checagem do `Host`, pool de 32 threads, JSON, assets com cache e `localIp()` | Regras, sala |
| `RoomServer` | `:social-core/room` | Rotas `/api/*` e assets do Social, mapeando para a `Room` | Estado |
| `Room` | `:social-core/room` | Lugares, tokens, **rostos** (bytes validados), presença, espectadores, trava, anfitrião, versão, long-poll com diferença por jogador, prazos, pausa, dedupe por `aid`, rejeição por `phase`, limite de taxa, **reações e apostas** (genéricas, fora dos jogos) | Regras de qualquer jogo |
| `Night` | `:social-core/night` | Fichas ★, `NightLog` (destaques e telemetria de diversão), `Host` (escolha de falas do apresentador), `Ceremony` (troféus), recordes | Desenho |
| `Game<S>` + `kit/*` | `:social-core/game` | Contrato e ajudantes: `Votes`, `Roles`, `Turns`, `Scores`, `ContentDeck` | Desenho |
| `games/<jogo>/<Jogo>Rules` | `:social-core/games` | Estado, fases, validação, fim, visões do celular e destaques | Compose, Android |
| `SocialViewModel` | `:app/social` | Abre e fecha a `Room`, ciclo de vida, `keepScreenOn`, `Sfx` e `Ambience`, conteúdo e TMDB, decodificação dos rostos fora da thread de UI | Regras |
| Telas, Palco e coreografias | `:app/social/ui` | Catálogo, sala, ficha, palco, resultado, cerimônia e as 6 coreografias | Estado durável |
| `<Jogo>Tv` | `:app/social/games` | O palco público daquele jogo | Validação |
| Celular | `assets/social/web` | Casca (entrada com selfie, sala, reconexão, pausa, relógio), widgets, envelope, reações e pôster | Lógica de jogo |

### 2.4 Protocolo HTTP [P]

| Método e rota | Corpo ou consulta | Resposta | Observações |
|---|---|---|---|
| `GET /` | — | casca HTML | A chave da sala vem no **fragmento** do QR (`/#k=…`) e nunca vai para o servidor nem para o histórico |
| `GET /a/<arquivo>?v=<versionCode>` | — | JS, CSS, arte | `Cache-Control: max-age=31536000, immutable` e gzip |
| `POST /api/join` | `{key, name, color?, device?}` | `{token, me, room}` | `key` inválida → 403. Sala cheia ou travada → entra como espectador. `device` liga ao perfil e devolve o mesmo lugar |
| `POST /api/face` | JPEG ≤ 32 KB (192×192) | `{ok}` | Decodificado e validado na TV. Fica só na memória, a não ser que a pessoa peça para lembrar |
| `GET /api/face/<id>` | `Authorization` | JPEG | Só para tokens da sala (o pôster no celular usa) |
| `GET /api/view?since=<hash>` | `Authorization: Bearer <token>` | `{hash, now, room:{…}, view: PhoneView}` | Segura até 25 s enquanto a visão **deste** jogador for igual a `since` |
| `POST /api/act` | `{aid, phase, type, data}` | `{ok}` ou `{ok:false, why}` | Os tipos `react` e `bet` ficam na `Room`. O resto vai para o jogo. `aid` repetido é ignorado e `phase` velha é rejeitada |
| `POST /api/me` | `{name?, color?, rememberFace?}` | `{me}` | Só no saguão |
| `POST /api/leave` | — | `{ok}` | Saída definitiva |

### 2.5 Segurança e privacidade local [P]

- **Entrar exige a chave da sala** (128 bits, `SecureRandom`, base64url), que só existe no QR e na URL mostrada na TV.
- **Cada lugar tem um token** (128 bits) e os IDs internos nunca servem de credencial. **Não existe rota que devolva a visão de outro jogador.**
- **Revogação:** "Nova sala" troca a chave e todos os tokens. "Remover" num avatar revoga um token.
- **Defesas baratas:** checagem do `Host` (contra DNS rebinding), tetos de corpo, 20 ações/s e 1 reação a cada 2 s por token, 5 entradas por minuto por IP, no máximo 12 jogadores e 4 espectadores, sem CORS, `Content-Security-Policy` restrita e nomes higienizados.
- **Rostos:** ficam na memória durante a noite. Só são gravados com "Lembrar meu rosto nesta TV" ligado, e **"Esquecer rostos"** aparece no Social. Nunca saem da rede de casa e não são servidos sem token.
- **Segredo físico:** envelope de segurar para abrir, telas com a mesma estrutura e tempo em todos os papéis, tarefa-isca na noite da Máfia e celular sempre mudo.
- **Limite aceito:** é HTTP sem TLS na rede de casa, então quem capturar o tráfego lê os papéis. É um risco documentado. A API Wake Lock não existe nesse contexto.

### 2.6 Contrato de jogo [P]

```kotlin
interface Game<S : Any> {
    val info: GameInfo                                        // id, nome, glifo, cor, categoria, jogadores (mín–máx, ideal), duração, dificuldade, estilo, ajustes, como jogar em 3 passos
    fun setup(ctx: Setup): S                                  // jogadores, ajustes, Random com semente, conteúdo, agora
    fun act(s: S, by: PlayerId, a: Action, now: Long): Step<S> // Ok(novo estado) | Rejected(motivo legível)
    fun host(s: S, cmd: HostCmd, now: Long): S                // Continuar / Pular / Revelar
    fun timeout(s: S, now: Long): S
    fun deadline(s: S): Long?
    fun phase(s: S): String                                   // chave da fase, contra ação atrasada
    fun mood(s: S): Mood                                      // SECRET | TALK | FOCUS: libera reações e o apresentador só em TALK
    fun phone(s: S, p: PlayerId): PhoneView                   // SÓ o que p pode ver
    fun left(s: S, p: PlayerId, now: Long): S
    fun over(s: S): Outcome?                                  // vencedores, fichas, destaques (Highlight), frase final
    fun sides(s: S): List<String>? = null                     // lados para a aposta da plateia ("Civis", "Infiltrados")
    fun secrets(s: S, p: PlayerId): Set<String>               // o que p NÃO pode ver; alimenta o teste de vazamento
}

class GameEntry<S : Any>(val rules: Game<S>, val stage: @Composable (S, StageUi) -> Unit)   // :app
```

`PhoneView` = `phase`, `title?`, `badge?` (envelope persistente), `body: Widget`, `buttons`, `endsAt?`. Widgets: **W1** espera (+ reações) · **W2** envelope · **W3** votar em jogador (com rostos) · **W4** escolher opção · **W5** texto curto · **W6** dial · **W7** Sim/Não/… · **W8** grade · **W9** manual · **W10** pronto.

### 2.7 Máquina de estados: o que é comum e o que é de cada jogo [P]

Não há um framework de máquina de estados genérica. Cada jogo tem o próprio `sealed interface Phase`. **O comum é infraestrutura e ajudantes de valor:**
- **Na `Room`** (vale para todos e o jogo nem vê): presença, pausa, dedupe, rejeição por `phase`, espectadores, reações, aposta da plateia, "Mais uma" e "Trocar de jogo".
- **Na `Night`:** fichas, destaques, apresentador e cerimônia, alimentados por `over()`.
- **No kit:** `Votes<K>`, `Roles.deal`, `Turns`, `Scores`, `ContentDeck`.
- **Em cada jogo:** fases, regras, vitória, visões e destaques.

Antídotos padrão contra travamento, cobrados pelo teste de contrato: toda fase de espera avança quando todos agiram, tem prazo opcional e aceita **"Continuar" no controle**.

### 2.8 Como adicionar o 16º jogo [P]

1. Escrever a **ficha de design de 1 página** (tese, momento uau, primeiro minuto, tempo morto).
2. Criar `:social-core/games/<novo>/<Novo>Rules.kt` com o kit, incluindo os destaques em `over()`.
3. Escrever `<Novo>RulesTest`. O **teste de contrato** genérico já cobre fuzz, término, determinismo, vazamento e serialização.
4. Criar `:app/social/games/<novo>/<Novo>Tv.kt` montado com `Stage` e as 6 coreografias.
5. Pôr conteúdo, glifo e cor, registrar uma linha em `SocialGames` e passar pelo **portão de diversão** (0.8).

Não há JS novo, rota nova nem mudança na `Room`. O apresentador e a cerimônia passam a reconhecer os destaques novos só com falas e troféus novos no pacote de conteúdo. **Exemplo opcional:** o próprio Match como jogo do Social, com `MatchRules` virando `MatchGame` e a revelação usando `Stamp`, sem tocar no Match atual.

### 2.9 O Match continua intacto [P]

O Match continua com servidor próprio na porta **8765**, `match.html`, rotas e JSON idênticos. O Social usa a porta **8766**. A extração é protegida por um **teste de caracterização** HTTP escrito **antes** da refatoração. As melhorias que o Match poderia herdar ficam como itens opcionais à parte.

---

## 3. Estrutura de módulos e diretórios [P]

```
settings.gradle.kts                 + include(":social-core")
gradle/libs.versions.toml           + plugin kotlin-jvm, kotlinx-coroutines-core
social-core/                        Kotlin/JVM puro (sem Android)
  src/main/kotlin/dev/jean/atvhome/social/
    net/LanServer.kt                extraído do MatchServer
    room/Room.kt  Seats.kt  Faces.kt  Deadlines.kt  RoomServer.kt  Protocol.kt
    night/Night.kt  NightLog.kt  Host.kt  Ceremony.kt  Records.kt
    game/Game.kt  GameInfo.kt  PhoneView.kt  Setup.kt  Highlight.kt
    kit/Votes.kt  Roles.kt  Turns.kt  Scores.kt  ContentDeck.kt
    games/<jogo>/<Jogo>Rules.kt     um pacote por jogo, ≤ ~400 linhas
  src/test/kotlin/...               regras, GameContractTest, RoomServerTest, CeremonyTest, HostTest, MatchServerContractTest
app/src/main/java/dev/jean/atvhome/
  data/MatchServer.kt               herda de LanServer; mesmas rotas e JSON
  ui/discover/Match.kt              usa o QrCode; mesmo visual
  ui/discover/Roulette.kt           usa o Reel extraído; mesmo comportamento
  ui/components/Reel.kt             extraído da Roleta
  social/SocialViewModel.kt  SocialGames.kt  Sfx.kt  Ambience.kt  Tts.kt (opcional)
  social/content/AssetContent.kt  Profiles.kt
  social/ui/SocialScreen.kt  GameSheet.kt  Stage.kt  Ceremony.kt  Surprise.kt  QrCode.kt  Curtain.kt  Vignette.kt
  social/ui/kit/  Avatar  PlayerStrip  Countdown  HostRibbon  Spotlight  Reactions  Scoreboard  ResultPanel  HowTo
  social/ui/motion/  Flip  Stamp  Tally  Headline  Burst  (Spotlight mora no kit)
  social/games/<jogo>/<Jogo>Tv.kt   palco ≤ ~400 linhas
app/src/main/res/drawable/social_*.xml   ilustrações e glifos em vetor
app/src/main/assets/social/
  web/index.html  app.js  app.css   ≤ 45 KB com gzip (casca, widgets, envelope, selfie, rabisco, pôster)
  content/<jogo>.json  host.json  awards.json
  sfx/*.ogg  ambience/*.ogg         próprios ou CC0, ≤ 1 MB ao todo
tools/social-perf.sh  tools/social-fun-report.sh
docs/social/PLANO.md  CONTEUDO.md  ARTE.md  SOM.md  fichas/<jogo>.md  playtests/  perf/
```

Mudanças pontuais em arquivos existentes: `HomeActivity.kt` (`Screen.Social`, ciclo de vida, Play/Pause), `HomeScreen.kt` (botão "Social"), `Theme.kt` (tokens `Stage.*`), `app/build.gradle.kts` (dependência do módulo) e `proguard-rules.pro` (conferir).

---

## 4. Fluxos de navegação [P]

**TV**
```
Home ─ botão "Social" ─▶ Social: palco com o QR; os rostos entram conforme os celulares chegam
  ├─ [Surpreenda-me] ─▶ rolos de jogos ─▶ vinheta ─▶ cortina ─▶ jogo
  ├─ Catálogo: "Para 5 pessoas agora" · Dedução e blefe · Conversa e risadas · Para dois · Cooperativos · Recordes da casa
  │     └─ OK num jogo ─▶ Ficha (3 passos · ajustes em chips · "Começar" focado · "Treino" na 1ª vez dos complexos)
  │           └─ Começar ─▶ vinheta ─▶ cortina ─▶ Palco (fases) ─▶ Revelação ─▶ Resultado (+ fichas voando para o Placar da noite)
  │                  ├─ "Mais uma" (focado) · "Trocar de jogo" · VOLTAR ─▶ Menu de pausa
  │                  └─ Menu de pausa: Continuar · Recomeçar rodada · Trocar de jogo · Encerrar a noite · Sair do Social
  └─ "Encerrar a noite" (ou sair depois de ≥ 2 jogos) ─▶ Cerimônia ─▶ Home (a sala espera 60 s)
```
- Teclas: **OK** faz a ação principal. **VOLTAR** no palco abre o menu de pausa e nunca sai direto. **Play/Pause** pausa e retoma. **MENU** abre a Central (os prazos pausam). **HOME** sai (60 s de tolerância). **Setas** navegam e também são o dial, a grade e a bomba.

**Celular**
```
QR ─▶ nome (lembrado) + selfie ou rabisco ─▶ "Entrar" ─▶ sala: seu rosto + "olhe para a TV"
   ─▶ o jogo começa e a tela troca sozinha (vibração leve) ─▶ envelope / decisão / "olhe para a TV" + reações
   ─▶ resultado ─▶ … ─▶ Cerimônia: "Seu troféu" + "Baixar o pôster da noite"
```
Exceções: sem rede → "Reconectando…" com nova tentativa crescente de 0,5 a 4 s e nova consulta imediata ao voltar a ficar visível. Token inválido com chave válida → reentra sozinho no mesmo lugar (pelo `device`). Chave inválida → "A sala fechou. Escaneie o QR de novo." Quem chega no meio vira **plateia** (reage e aposta, e entra na próxima rodada). Quem cai fica cinza na TV depois de 20 s e não perde o lugar.

---

## 5. Os 15 jogos [P]

### 5.0 Vocabulário comum

- **Celular:** W1 espera + reações · W2 envelope · W3 votar (com rostos) · W4 escolher · W5 texto · W6 dial · W7 Sim/Não/… · W8 grade · W9 manual · W10 pronto.
- **Palco:** T1 `Stage` · T2 `Avatar`/`PlayerStrip` · T3 `Countdown` · T4 `HostRibbon` (falas do apresentador e narração) · T5 `Tally` (apuração) · T6 `Flip` · T7 `Scoreboard` · T8 `ResultPanel` · T9 `HowTo`/treino · T10 `QrPanel` · T11 `Spotlight` + **Holofote** (10 s para falar, com contagem) · T12 `Stamp` · T13 `Headline` · T14 `Burst`.
- **Núcleo:** C1 sala · C2 fases e prazos · C3 `Votes` · C4 `Roles` · C5 `Turns` · C6 `Scores` · C7 `ContentDeck` · C8 `Night` (fichas, destaques, apresentador).
- **Nomes:** originais, para não usar marcas de terceiros. "Sintonia" (4) colidia com "Sintonia de Casal" (14), então o 4 virou **Frequência**.

| # | Pedido | Nome proposto | Categoria | Jogadores (ideal) | Duração |
|---|---|---|---|---|---|
| 1 | Undercover / Mr. White | **Infiltrados** (+ Camaleão) | Dedução e blefe | 4–10 (5–8) | 12–15 min (Camaleão: 4 min) |
| 2 | Spyfall | **Lugar Secreto** | Dedução e blefe | 4–8 (5–7) | 6–8 min por rodada |
| 3 | Máfia de Uma Noite | **Máfia de Uma Noite** | Dedução e blefe | 5–10 (6–9) | 8–12 min |
| 4 | Wavelength / Sintonia | **Frequência** | Conversa / Cooperativo | 2–10 (4–8) | 15–20 min |
| 5 | Insider | **Quem Sabia?** | Dedução e blefe | 4–8 (5–7) | 8–10 min |
| 6 | Psicólogo | **Psicólogo** | Conversa e risadas | 4–12 (5–10) | 8–10 min por rodada |
| 7 | Missões Secretas | **Missões Secretas** | Conversa e risadas | 2–10 (3–8) | 15/30/60 min, em segundo plano |
| 8 | Tribunal dos Amigos | **Tribunal dos Amigos** | Dedução e blefe | 4–8 (5–7) | 20–30 min |
| 9 | Quem é Mais Provável? | **Quem é Mais Provável?** | Conversa e risadas | 3–12 (4–10) | 8–10 min |
| 10 | Duas Verdades e Uma Mentira | **Duas Verdades e Uma Mentira** | Conversa e risadas | 3–10 (4–8) | 12–15 min |
| 11 | Codenames: Duet | **Dupla Secreta** | Para dois / Cooperativo | 2 (até 4 em lados) | 15 min |
| 12 | Black Stories | **Mistério Sim ou Não** | Para dois / Cooperativo | 2–6 (2–5) | 10–20 min |
| 13 | Filme em 5 Pistas | **Filme em 5 Pistas** | Para dois / Conversa | 2–6 | 10–15 min |
| 14 | Sintonia de Casal | **Sintonia de Casal** | Para dois | 2 | 10–15 min |
| 15 | Desarme a Bomba | **Desarme a Bomba** | Cooperativo | 2–4 (2–3) | 5 min por bomba |

Recomendações por número de pessoas (fileira "Para N pessoas agora" e sorteio do Surpreenda-me):
- **2:** Sintonia de Casal, Dupla Secreta, Filme em 5 Pistas, Mistério, Frequência (cooperativo), Bomba, Missões.
- **3:** os de 2, mais Quem é Mais Provável e Duas Verdades.
- **4–5:** Infiltrados, Lugar Secreto, Quem Sabia?, Psicólogo, Frequência (times), Tribunal.
- **6–8:** todos os de grupo, mais Máfia.
- **9–12:** Quem é Mais Provável, Psicólogo, Infiltrados, Máfia, Frequência e Missões.

> Mistério Sim ou Não: o pedido diz 2 jogadores, mas a mecânica funciona igual com até 6. Proponho 2–6.

### 5.1 Infiltrados (Undercover / Mr. White · modo Camaleão)
| | |
|---|---|
| Mecânica e objetivo | Os civis recebem a palavra A, os infiltrados a palavra B, parecida com A (sem saber que são infiltrados), e o **Sr. Branco** não recebe palavra (opcional, com 5+). Cada um diz uma pista em voz alta e todos votam para eliminar. Os civis vencem quando eliminam infiltrados e Sr. Branco. Os infiltrados vencem quando ficam tantos quanto os civis. O Sr. Branco eliminado tenta adivinhar a palavra dos civis e, se acertar, vence sozinho |
| Jogadores | 4–10 (5–8). 4 → 1 infiltrado. 5–6 → 1 + Sr. Branco. 7–8 → 2 + 1. 9–10 → 3 + 1 |
| Duração | Partida de 12–15 min. Camaleão: 4 min |
| TV | Ordem de fala de rosto em rosto, com uma barra de 12 s que avança sozinha (o controle adianta), contagem de votos, apuração com as fichas voando até o rosto e a eliminação com **`Flip` + `Stamp`** na cor do papel. No fim, as duas palavras lado a lado |
| Celular | W2 com a palavra no envelope (ou "Você é o Sr. Branco"), W3 na votação, W5 no palpite do Sr. Branco. Os eliminados ganham o palpite fantasma |
| Ações no celular | Segurar para abrir e 1 toque por votação |
| Rodada | Distribuição → "Todos viram?" → rodada de pistas → votação de 40 s → empate: os empatados ganham mais 1 pista no **Holofote** e há nova votação; segundo empate, ninguém sai → revelação → **Holofote: última palavra** (10 s) → confere a vitória |
| Início e fim | A ficha define modo, Sr. Branco e tema. No fim, revelação de todos os papéis e fichas (civis +3, infiltrado +3, Sr. Branco acertando +5) |
| **Assinatura e momento uau** | **Última palavra:** o eliminado ganha 10 s de holofote para se defender ou acusar. **Sr. Branco no limite:** a palavra que ele digita aparece **letra por letra** na TV, com rufar, antes do carimbo ACERTOU ou ERROU |
| Adaptações que somam | **Camaleão:** grade 4×4 de palavras do tema na TV, todos sabem a célula secreta menos o camaleão, 1 rodada e, se for pego, ele escolhe a palavra entre as 16. **Rodada relâmpago.** Temas brasileiros. O Sr. Branco nunca fala primeiro |
| Riscos e antídotos | Eliminado parado → palpite fantasma (+1). Pares fáceis ou difíceis demais → curadoria por "distância", 200+ pares. Repetição → `ContentDeck` |
| Complexidade | Média a baixa |
| Reuso | C1–C8, W2–W5, W10, T1–T8, T11, T12 |

### 5.2 Lugar Secreto (Spyfall)
| | |
|---|---|
| Mecânica e objetivo | Todos menos o espião recebem um **local** e um papel ("Padaria · Padeiro"). As perguntas circulam em voz alta. O espião tenta descobrir o local e os outros tentam desmascará-lo |
| Jogadores | 4–8 (5–7). Com 8, opção de 2 espiões |
| Duração | 7 min por rodada (5 min no modo rápido) |
| TV | **A galeria pública dos 24 locais** como **cartões-postais ilustrados** (o espião olha para a TV como todo mundo), o tempo grande e os momentos de acusação (*"Bruno acusa Carla!"*, com os dois rostos frente a frente) |
| Celular | W2 com local e papel (ou "Você é o espião") + **Acusar** e, para o espião, **Sei o local**. A estrutura é igual nos dois papéis |
| Ações no celular | Segurar para ver. Uma acusação por pessoa por rodada (W3), voto Sim/Não sobre a acusação (W7) e o palpite do espião (W4) |
| Rodada | Distribuição → perguntas → acusação (o tempo pausa, Holofote de 10 s para o acusado se defender) → só o voto unânime revela → espião acerta, ou civis acertam, ou erram → se o tempo acabar, há votação final, e empate dá vitória ao espião |
| Início e fim | Série de rodadas com espião sorteado. Fichas: espião +3 (+2 se acertar o local), civis +3 (+1 para quem acusou certo) |
| **Assinatura e momento uau** | Quando o espião chuta, o **postal escolhido gira até o centro** e vira para o lado ACERTOU ou ERROU. No fim, o espião aparece num cartaz de **"PROCURADO"** com o rosto dele |
| Adaptações que somam | O espião pode **riscar locais** no celular. Locais brasileiros (feira, rodoviária, churrascaria, velório de tia) |
| Riscos e antídotos | Perguntas vagas → a ficha traz 3 exemplos de boas perguntas. Chute cedo por sorte → só um palpite, e errar perde |
| Complexidade | Média (24 ilustrações de postal no estilo da casa) |
| Reuso | C2, C3, W2–W4, W7, T3, T5, T6, T11 |

### 5.3 Máfia de Uma Noite
| | |
|---|---|
| Mecânica e objetivo | Uma única noite com poderes simples e 3 cartas no centro, depois o dia, a discussão e um voto. A cidade vence se eliminar um mafioso. Papéis originais: **Mafioso** (os mafiosos se conhecem), **Informante** (sabe quem são, é da cidade), **Detetive** (olha 1 jogador ou 2 cartas do centro), **Batedor** (troca a própria carta com a de alguém e vê a nova), **Fofoqueiro** (troca as cartas de dois outros sem ver), **Sonâmbulo** (troca com o centro sem ver), **Vigia** (vê a própria carta no fim), **Cidadão** |
| Jogadores | 5–10 (6–9) |
| Duração | Noite de 1,5 min, dia de 4 min, revelação de 1 min |
| TV | **Noite:** tela escura em tons derivados do verde, lua, narração em texto (voz por TTS opcional) e grilos ao fundo. **Dia:** contagem e apuração |
| Celular | Envelope com o papel e uma linha de explicação. Na noite, **todos** recebem uma tela a cada etapa, e quem não age faz uma tarefa-isca, para não se denunciar pelo toque. W3 no voto |
| Ações no celular | 1 toque por etapa da noite (todos) e 1 voto |
| Rodada | Distribuição → noite em ordem fixa (etapas de 8–10 s, mesmo para papéis ausentes) → dia → voto → apuração → todas as cartas viram → **Jornal da Vila** → reprise da noite |
| Início e fim | A ficha sugere a combinação de papéis pelo número de pessoas. Uma rodada por partida |
| **Assinatura e momento uau** | **Jornal da Vila:** a revelação é a primeira página de um jornal que gira e para (`Headline`), com a foto de quem morreu e a manchete (*"BRUNO ENCONTRADO DORMINDO NO SERVIÇO: ERA O MAFIOSO"*). Depois vem **"O que aconteceu à noite"**: as cartas viajam entre os rostos, troca a troca |
| Adaptações que somam | **Treino guiado** na primeira vez. Modo iniciante (Mafioso, Detetive e Cidadão). Manchetes com modelos variados |
| Riscos e antídotos | Confusão de papéis → treino e explicação no celular. Ninguém fala → "dicas de abertura" do apresentador no começo do dia. Vazamento → noite uniforme coberta por teste |
| Complexidade | **Alta** |
| Reuso | C2–C4, C8, W2–W4, T4–T6, T13, `Ambience`, `Tts` |

### 5.4 Frequência (Wavelength)
| | |
|---|---|
| Mecânica e objetivo | A TV mostra um espectro (*"Coisa de tio ↔ Coisa de sobrinho"*). O **sintonizador** vê o alvo no celular e dá uma pista em voz alta. O time gira o dial **na TV com o controle remoto** (passa o controle de mão em mão) e confirma com OK. O time adversário aposta "mais à esquerda ou à direita" |
| Jogadores | 2–10. Com 2–3 é cooperativo (7 cartas). Com 4+ há 2 times e ganha quem chegar primeiro a 10 |
| Duração | 15–20 min |
| TV | Dial em meia-lua em Canvas, **com um clique sonoro a cada passo do controle** (sensação física), rótulos do espectro e placar dos times |
| Celular | Sintonizador: W6 só de leitura com o alvo e "Trocar carta (1×)". Os outros: W1 |
| Ações no celular | Só o sintonizador, no máximo 1 toque. O resto é feito no controle remoto |
| Rodada | Carta → alvo → pista falada → o time discute e gira → OK → o adversário escolhe esquerda ou direita → **revelação** → pontos → passa o papel |
| Início e fim | Cooperativo ou times. **A última carta vale o dobro** (virada possível) |
| **Assinatura e momento uau** | A **tampa do dial abre devagar** com rufar crescente. **Na mosca** dispara `Burst` + fanfarra, e o apresentador fala o nome do sintonizador |
| Adaptações que somam | Espectros brasileiros e engraçados. Modo relâmpago com 5 cartas |
| Riscos e antídotos | Sintonizador travado → trocar a carta uma vez, tempo sugerido de 60 s. Controle preso → *"passe o controle para o time Azul"* |
| Complexidade | Média |
| Reuso | W6, C5, C6, C8, T1, T7, T8, T14 |

### 5.5 Quem Sabia? (Insider)
| | |
|---|---|
| Mecânica e objetivo | O **Mestre** (público) sabe a palavra e responde Sim, Não ou Não sei. O **Infiltrado** (secreto) também sabe e tenta guiar o grupo sem dar bandeira. O grupo tem 5 min para achar a palavra e depois vota em quem era o infiltrado. Se não acharem, ninguém ganha |
| Jogadores | 4–8 (5–7) |
| Duração | 8–10 min |
| TV | Tempo, **cada resposta do Mestre vira um carimbo** com contador e histórico das últimas 8 (ajuda a memória e mantém os olhos na TV), *"Encontraram: GIRAFA em 3:12"* |
| Celular | Mestre: envelope + W7 + "Acertaram!". Infiltrado: envelope com a palavra. Comuns: envelope "Você é comum", com a mesma estrutura. Todos: W3 |
| Ações no celular | Mestre: 1 toque por pergunta. Os outros: 1 voto |
| Rodada | Papéis → perguntas → palavra encontrada → discussão com o tempo que sobrou → voto → revelação |
| Início e fim | Uma rodada por partida, com o Mestre em rodízio |
| **Assinatura e momento uau** | **`Spotlight`:** um círculo de luz varre os rostos devagar, para no infiltrado e o carimbo cai. Se o infiltrado escapou, o apresentador: *"Ninguém desconfiou da {nome}. Assustador."* |
| Adaptações que somam | Dificuldade da palavra. Destaque "Infiltrado de ouro" para a cerimônia |
| Riscos e antídotos | Mestre cansado → rodízio. Palavra impossível → dificuldade na ficha |
| Complexidade | Baixa a média |
| Reuso | W2, W3, W7, C2, C3, C8, T3, T5, T11, T12 |

### 5.6 Psicólogo
| | |
|---|---|
| Mecânica e objetivo | Todos, menos o psicólogo, seguem uma **regra secreta** ao responder (*"responda como se fosse a pessoa à sua esquerda"*). O psicólogo pergunta o que quiser e tenta descobrir a regra |
| Jogadores | 4–12 (5–10) |
| Duração | 8–10 min por rodada |
| TV | Ambiente de **consultório** (divã ilustrado), tempo, psicólogo em destaque, 3 palpites como vidas e contador de "consultas" |
| Celular | Os outros: envelope com a regra **e 2 exemplos** (essencial para aplicarem certo). Psicólogo: W1 + "Vou chutar". Ao chute, os outros recebem W7 ("Ele acertou?") e a maioria decide |
| Ações no celular | 1 toque por chute |
| Rodada | Regra → perguntas → chute → maioria confirma → acertou ou perde uma vida → revela |
| Início e fim | Rodízio do psicólogo. Fichas pelo tempo que sobrou |
| **Assinatura e momento uau** | **"Diagnóstico":** a regra é revelada como um laudo carimbado, com o tempo (*"Diagnóstico em 3:12 · 14 consultas"*). Se ele não acertar, o carimbo é *"Caso encerrado sem cura"* |
| Adaptações que somam | Regras em 3 níveis. **Regra dupla** para grupos experientes |
| Riscos e antídotos | Regra aplicada errado → exemplos no celular e regras testáveis. Fácil demais → níveis |
| Complexidade | Baixa |
| Reuso | W1, W2, W7, C2, C6, T3, T12 |

### 5.7 Missões Secretas
| | |
|---|---|
| Mecânica e objetivo | Cada pessoa tem **3 missões secretas** (*"faça alguém dizer 'pipoca'"*). Cumprir dá 1, 2 ou 3 pontos. Quem desconfia pode **desmascarar**: se acertar, a missão queima e o acusador ganha +1 |
| Jogadores | 2–10 (3–8) |
| Duração | 15, 30 ou 60 min, ou "até 10 pontos". É um **jogo de fundo** |
| TV | **Modo ambiente**: placar discreto e um mural de eventos (*"Ana cumpriu uma missão! +2"*, sem dizer qual), **0 frames parado** |
| Celular | Envelope com as missões, "Cumpri" e "Desmascarar" (W3 + confirmação de quem foi acusado em W7) |
| Ações no celular | Poucas e esparsas |
| Rodada | Contínua: cumpriu → missão nova. Desmascarado → queima e vem outra. 1 acusação a cada 3 min por pessoa |
| Início e fim | Tempo ou meta. Fim com o **Dossiê** |
| **Assinatura e momento uau** | **Dossiê final:** cada missão cumprida aparece com o rosto de quem cumpriu e de quem caiu sem perceber (*"Bia fez o Caio dizer 'pipoca' às 21:42"*). É o momento de gargalhada |
| Adaptações que somam | **Missões em dupla**, ligadas entre duas pessoas sem que uma saiba da outra. Experimental para depois: seguir com o jogo enquanto a TV mostra outra coisa |
| Riscos e antídotos | Honestidade → confirmação de quem foi acusado. Missões impossíveis → 3 níveis e troca de 1 por partida |
| Complexidade | Baixa |
| Reuso | W2, W3, W7, C6, C7, C8, T7, T8 |

### 5.8 Tribunal dos Amigos
| | |
|---|---|
| Mecânica e objetivo | Um crime absurdo (*"O sumiço do bolo da Tia Neide"*). Cada jogador é suspeito, com **3 atributos** (local, objeto, horário) numa combinação única. Um é o **culpado**. A cada rodada, a TV revela uma **prova pública** com um atributo do culpado. Cada um tem no celular a própria ficha (a verdade) e **2 fatos sobre outros**. Inocentes dizem a verdade e o culpado mente. No fim, veredito por voto |
| Jogadores | 4–8 (5–7). Com 6+, papel opcional de **Cúmplice** (mente para proteger) |
| Duração | 20–30 min |
| TV | **Mural de investigação** (cortiça assada em bitmap), **fichas criminais com as selfies** (régua de altura atrás), provas presas que viram cartão e a vez do interrogado no Holofote |
| Celular | Envelope com o dossiê, **"Objeção!"** (2 por julgamento) e W3 no veredito |
| Ações no celular | Ler no começo, até 2 objeções e 1 voto |
| Rodada | Caso narrado → prova 1 → interrogatório → prova 2 → interrogatório → prova 3 → veredito → revelação do caminho lógico |
| Início e fim | Uma partida por caso. "Mais uma" sorteia outro caso e outros atributos |
| **Assinatura e momento uau** | **Objeção!:** quem aperta no celular faz a TV piscar com o rosto e o grito (`Stamp`) e ganha **10 s de Holofote**, com o tempo congelado. **Veredito:** o martelo bate três vezes e o carimbo CULPADO ou INOCENTE cai sobre a ficha criminal |
| Adaptações que somam | Casos gerados por **modelo + atributos**: solucionáveis por construção e com replay infinito a partir de 10 modelos. Treino na primeira vez |
| Riscos e antídotos | Sobrecarga → só 3 atributos e 3 rodadas. Descoberto cedo → provas aos poucos. Caso sem solução → teste de propriedade |
| Complexidade | **Alta** |
| Reuso | C2–C4, C8, W2, W3, T4–T6, T11, T12 |

### 5.9 Quem é Mais Provável? — **jogo-esqueleto da Fase 1**
| | |
|---|---|
| Mecânica e objetivo | A TV mostra uma pergunta (*"Quem é mais provável de esquecer o próprio aniversário?"*), todos votam em alguém (vale votar em si) e a TV revela |
| Jogadores | 3–12 (4–10) |
| Duração | 8–10 min (10 perguntas) |
| TV | Cartão da pergunta, bolinhas de quem votou e a revelação com `Tally` (as fichas **voam dos rostos de quem votou até o rosto escolhido**) e a coroa |
| Celular | W3 com os rostos (1 toque) |
| Ações no celular | 1 toque por pergunta |
| Rodada | Pergunta → voto (20 s) → revelação → **"Defenda-se"**: o mais votado ganha 10 s de Holofote → próxima |
| Início e fim | 10 perguntas. **As duas últimas valem o dobro.** No fim, os **"Títulos da noite"** |
| **Assinatura e momento uau** | **Unânime:** quando todos votam na mesma pessoa, toca uma sirene e o carimbo UNÂNIME cai sobre o rosto. **Defenda-se:** 10 s de holofote viram 10 s de risada |
| Adaptações que somam | **Aposta** (também tente prever quem vai ganhar, +1). Categorias: família, amigos e picante (opcional, desligada por padrão) |
| Riscos e antídotos | A mesma pessoa sempre escolhida → perguntas neutras ou positivas, o apresentador nota com carinho e há "Pular pergunta" no controle |
| Complexidade | **Baixa**: exercita entrada, rostos, voto, revelação, rodadas e fichas com regras mínimas |
| Reuso | W3, C3, C6–C8, T5, T7, T8, T11, T12 |

### 5.10 Duas Verdades e Uma Mentira
| | |
|---|---|
| Mecânica e objetivo | O narrador da vez conta **em voz alta** 3 fatos numerados. Todos votam em qual é a mentira e o narrador marca no celular qual era |
| Jogadores | 3–10 (4–8) |
| Duração | 12–15 min |
| TV | Narrador no Holofote, cartões grandes "1 2 3" e o **polígrafo** |
| Celular | Narrador: W4 secreto + sugestões de tema no "pensando…" de 60 s. Os outros: W4 (1/2/3) |
| Ações no celular | 1 toque |
| Rodada | Pensar → contar → votar → revelar → próximo |
| Início e fim | Uma volta. Acertou +1, narrador +1 por pessoa enganada (destaque "Cara de Pau") |
| **Assinatura e momento uau** | **Detector de mentiras:** um polígrafo desenhado na TV cuja agulha **treme a cada voto que chega** (sem mostrar em quê) e dispara na revelação, com o carimbo MENTIRA no cartão certo |
| Adaptações que somam | Modo escrito opcional (W5, os fatos aparecem na TV) para grupos tímidos |
| Riscos e antídotos | Branco na hora de pensar → sugestões. Volta longa → 1 volta |
| Complexidade | Baixa |
| Reuso | W4, W5, C3, C5, C6, C8, T5, T7, T11, T12 |

### 5.11 Dupla Secreta (Codenames: Duet)
| | |
|---|---|
| Mecânica e objetivo | Cooperativo. Grade 5×5 de palavras na TV. Cada lado tem uma **chave privada** (9 agentes, 3 assassinos) com sobreposição parcial: 15 agentes ao todo. Os jogadores se alternam dando pista de uma palavra + número. Encontrar os 15 em 9 turnos vence. Um assassino derrota |
| Jogadores | 2 (até 4 em dois lados que compartilham a chave) |
| Duração | 15 min |
| TV | Grade com **cartões que viram**, turnos restantes e de quem é a pista. **Quem adivinha escolhe com o direcional do controle**, olhando a TV |
| Celular | W8 com a sua chave em cores (só leitura) |
| Ações no celular | Nenhuma |
| Rodada | Pista falada → escolha no controle → acerto continua, neutro passa a vez e gasta turno, assassino derrota → morte súbita no fim |
| Início e fim | Vitória com 15 agentes |
| **Assinatura e momento uau** | **Campanha pelo Brasil:** um mapa com 12 cidades. Cada vitória **acende a rota** até a próxima cidade (menos turnos, palavras mais difíceis). O progresso fica salvo por dupla |
| Adaptações que somam | Chave procedural (a mecânica é livre). 500 palavras originais |
| Riscos e antídotos | Pista ambígua → regra curta na ficha. Grade ruim → o gerador evita palavras parecidas |
| Complexidade | Média |
| Reuso | W8, C5, T1, T6, T8, `Records` |

### 5.12 Mistério Sim ou Não (Black Stories)
| | |
|---|---|
| Mecânica e objetivo | O **Guardião** tem a história completa no celular. A TV mostra só o enigma. Os outros perguntam em voz alta e o Guardião responde: Sim, Não, Irrelevante ou Quase |
| Jogadores | 2–6 (2–5) |
| Duração | 10–20 min por mistério |
| TV | **Atmosfera noir**: tipografia dramática, chuva ao fundo (som ambiente), cada resposta vira carimbo com histórico, dicas liberadas pelo Guardião |
| Celular | Guardião: envelope com a solução e 3 dicas + W7 + "Liberar dica" + "Resolvido!". Os outros: W1 |
| Ações no celular | Guardião: 1 toque por pergunta |
| Rodada | Enigma → perguntas → dicas (sugeridas depois de 4 min) → resolvido ou desistência → revelação |
| Início e fim | Um mistério por partida, com o Guardião em rodízio |
| **Assinatura e momento uau** | **A história se escreve sozinha:** a solução aparece na TV como máquina de escrever, com o som das teclas, e a última frase fica sozinha na tela |
| Adaptações que somam | Nível "família" (menos macabro). Dificuldade |
| Riscos e antídotos | Travar → escada de dicas. **Conteúdo protegido** → mistérios 100% originais |
| Complexidade | Baixa |
| Reuso | W1, W2, W7, T4, T12, `Ambience` |

### 5.13 Filme em 5 Pistas — **integrado ao launcher**
| | |
|---|---|
| Mecânica e objetivo | Um filme secreto e pistas que ficam cada vez mais fáceis. Cada pessoa responde **uma vez** por filme, quando quiser: na pista k vale 6 − k e errar vale 0. É um jogo de "arrisco agora ou espero?" |
| Jogadores | 2–6 |
| Duração | 10–15 min (5 filmes) |
| TV | Pistas em texto que se empilham (1: ano, gênero e duração · 2: tagline sem nomes · 3: dois atores secundários · 4: personagem e sinopse mascarada · 5: título com letras ocultas) **e um pôster pixelado que fica nítido a cada pista**. **6 opções A–F** na tela (iscas do mesmo gênero e década) e quem já travou |
| Celular | W4 com A–F (ou "Esperar") |
| Ações no celular | 1 toque por filme |
| Rodada | Prepara (TMDB + pré-carregamento) → pistas a cada 15 s ou quando todos travaram → revelação → próximo |
| Início e fim | Fonte: "Em alta", **"Nos meus streamings"** ou **"Da Minha Lista"**. **Final:** *"Vamos assistir?"* mostra quais dos 5 filmes estão nos seus streamings, com **"Assistir no Netflix"** (`HomeViewModel.watch`) |
| **Assinatura e momento uau** | **Pôster pixelado:** decodificado em 8×12 → 16×24 → 32×48 → 64×96 → nítido, ampliado sem suavização (`FilterQuality.None`). **Custo de GPU praticamente zero**, e a tensão cresce sozinha. Na revelação, o pôster fica nítido de uma vez e o logo do título aparece |
| Adaptações que somam | Dificuldade pela popularidade. "Cinéfilo da noite" para a cerimônia |
| Riscos e antídotos | Sem internet ou sem token → a ficha avisa. Imagem lenta → pré-carregamento (padrão do `prefetch`, `HomeViewModel.kt:170-178`). Nome vazando → máscara com teste |
| Complexidade | Média |
| Reuso | `Catalog.discover/details`, `TmdbClient.image`, Coil, `PosterCard`, `TitleArt`, `HomeViewModel.watch/myProvidersOf`, `MyList`, W4, C6, C8, T5, T7, T8 |

### 5.14 Sintonia de Casal
| | |
|---|---|
| Mecânica e objetivo | Perguntas sobre um dos dois (*"Qual seria o jantar perfeito da Ana?"*). A pessoa responde sobre si e a outra tenta prever, **ao mesmo tempo**. Coincidiu, pontua |
| Jogadores | 2 |
| Duração | 10–15 min (12 perguntas, alternando sobre quem) |
| TV | A pergunta, os dois rostos com "travado ✓", **revelação em tela dividida** e o **termômetro da sintonia** |
| Celular | W4 (opções ou isso-ou-aquilo) ou W6 (escala, que pontua pela proximidade) |
| Ações no celular | 1 toque por pergunta |
| Rodada | Pergunta → os dois respondem → revelação → **"Defenda sua resposta"** (10 s de Holofote quando divergem) → próxima |
| Início e fim | Categorias (leve, nostalgia, "nós dois", picante opcional). **A pergunta final vale o dobro.** Fim: *"Sintonia 82% · Almas gêmeas"* |
| **Assinatura e momento uau** | **Histórico do casal:** a cada noite, a TV mostra a linha da sintonia ao longo das semanas (*"Recorde de vocês: 91%, em 12 de outubro"*) e um título novo quando o recorde cai |
| Adaptações que somam | **Relâmpago:** 10 isso-ou-aquilo de 5 s. Sem repetir pergunta para o mesmo casal |
| Riscos e antídotos | Briga → tom leve, sem "defeitos". Repetição → 300 perguntas |
| Complexidade | Baixa |
| Reuso | W4, W6, C6–C8, T5, T7, T8, T11, `Records` |

### 5.15 Desarme a Bomba — experiência elaborada
| | |
|---|---|
| Mecânica e objetivo | Uma bomba na TV com 3–6 **módulos**, número de série, pilhas e etiquetas, tempo e 3 erros permitidos. O **desarmador usa o controle remoto** (setas escolhem o módulo, que cresce com zoom; OK age; segurar OK funciona no Botão). Os **especialistas** leem o **manual no celular** e orientam por voz |
| Jogadores | 2–4 (2–3). Com 3–4, o **manual é dividido** (cada um tem parte dos módulos) |
| Duração | 5 min por bomba |
| TV | Caixa **desenhada uma vez** (camada em cache) e só as partes vivas redesenham: 7 segmentos (1 Hz, 10 Hz nos últimos 10 s), LEDs e o módulo em foco. Erro: flash vermelho curto (sobreposição pronta) + tremor (`graphicsLayer`) + som. Tique ambiente e **batida de coração nos últimos 30 s** |
| Celular | W9 com páginas e abas por módulo: texto, tabelas e **diagramas em SVG gerados pela TV a partir das mesmas regras**. Vibra a cada erro (Android) |
| Ações no celular | Nenhuma |
| Módulos originais | MVP: **Fios**, **Botão** (segurar e soltar no dígito certo), **Símbolos** (4 glifos próprios na ordem do manual), **Labirinto** (levar o ponto às cegas com o direcional). Depois: **Sequência Luminosa**, **Senha** (rodas de letras), **Disjuntores**, **Pisca-pisca** |
| Rodada | Bomba gerada por semente → módulo a módulo → vence com todos desarmados, explode no tempo ou no 3º erro |
| Início e fim | **Progressão** de 10 níveis com **crachás do Esquadrão** (rosto + patente), **"Bomba do dia"** (semente pela data) e **recorde da casa** |
| **Assinatura e momento uau** | **O último segundo:** desarmar com menos de 3 s faz o tempo **desacelerar** (0:02… 0:01…) antes do verde. Em seguida, o **replay da desativação**: a linha do tempo com cada ação (*"cortou o fio azul aos 0:03"*). Explosão: flash branco, silêncio de 1 s e o crachá chamuscado |
| Adaptações que somam | Glifos definidos uma vez como dados de caminho, servindo ao desenho na TV (`PathParser`) e ao SVG do manual. Treino guiado |
| Riscos e antídotos | Manual confuso → teste com pessoas antes de entrar cada módulo. GPU → orçamento próprio |
| Complexidade | **Muito alta** |
| Reuso | C1, C2, C8, W9, T1, T8, T14, `Sfx`, `Ambience`, `Records` |

---

## 6. Plano de desempenho [P]

### 6.1 Estratégias
1. **Custo zero quando não está em uso:** nada no `App.onCreate`. Classes, conteúdo, sons, rostos e servidor só nascem ao entrar. Fora do Social, o único custo é o botão no topo e ~2 MB no APK.
2. **Um servidor, uma sala, uma porta.** Pool de no máximo 32 threads. Parar a sala devolve as threads à base.
3. **Long-poll com diferença por jogador:** só acorda o celular cuja tela mudou. Visões abaixo de 2 KB, assets com gzip e cache imutável. Reações vão como uma lista curta, com no máximo 6 visíveis.
4. **Recomposição mínima:** estado imutável em fatias, chaves estáveis, animação na fase de desenho, `Path` e `Brush` guardados em `drawWithCache` e nenhuma alocação por frame.
5. **GPU:** sem sombra borrada, sem degradê de tela cheia ao vivo (holofote, cortiça, noite e jornal **assados em bitmap**, como o `Scrim`), sem vídeo e sem WebView na TV. A cortina é feita de 2 retângulos sólidos.
6. **Animação por evento e finita:** telas paradas **desenham 0 frames**. Cada coreografia dura ≤ 2,5 s e é medida uma vez na TV. O cronômetro dá passos de 1 Hz e só acelera nos últimos 10 s.
7. **Imagens:** rostos de 192×192 (12 × ~147 KB ≈ 1,8 MB), ilustrações em vetor, TMDB em no máximo w780, pôster pixelado decodificado em miniatura e pré-carregamento antes da revelação.
8. **QR compacto:** a matriz mínima (~33×33 px, < 10 KB) com `FilterQuality.None`. No Match, é 1 MB a menos.
9. **Recursos com começo e fim:** o conteúdo é lido ao começar. O `SoundPool` (kit) carrega ao entrar e libera ao sair. `Ambience` é um `MediaPlayer` por vez. O TTS só existe durante a Máfia, se ligado.
10. **`keepScreenOn`** só com a sala ativa e ao menos 1 jogador.

### 6.2 Orçamentos (metas de aprovação)

Base: Home com PSS de 123 MB e 66 threads (1.3).

| Métrica | Meta |
|---|---|
| PSS fora do Social (depois de visitá-lo e esperar 60 s) | ≤ +3 MB acima da base. Threads em ±2 |
| PSS na sala com 8 bots e rostos | ≤ +14 MB |
| PSS no jogo (todos menos a Bomba), com som ambiente | ≤ +22 MB |
| PSS na Bomba | ≤ +32 MB |
| PSS depois de 2 h alternando jogos | inclinação ≤ 2 MB/h depois do aquecimento |
| Threads com 12 celulares | ≤ base + 30 |
| Abrir o Social (quente): do clique ao 1º frame | ≤ 300 ms. QR visível em ≤ 600 ms |
| Frames durante interação e coreografias | ≤ 10% com jank, p90 ≤ 20 ms, p95 ≤ 33 ms (a base atual é 26,6%, 26 ms e 42 ms) |
| Sala ou modo ambiente parados | Δ de frames = 0 em 10 s |
| CPU do launcher parado no Social | ≤ 2% em média |
| Latência da ação do celular até a TV desenhar | p95 ≤ 150 ms |
| Latência da mudança na TV até o celular atualizar | p95 ≤ 300 ms no Wi-Fi |
| Página do celular | ≤ 45 KB com gzip. Entrar com selfie em ≤ 8 s, sem selfie em ≤ 3 s |
| APK | ≤ +2 MB (a base é 2,52 MB; inclui sons) |

### 6.3 Como medir
- `tools/social-perf.sh <cenário>` (adb): `dumpsys gfxinfo <pkg> reset` → cenário → gfxinfo + `dumpsys meminfo` + `/proc/<pid>/status` + `top`. Mediana de 3 execuções, comparada à base e gravada em `docs/social/perf/`.
- Os cenários são roteiros com `adb shell input keyevent` e **bots** (OkHttp, já dependência) com `-Dsocial.tv=192.168.1.3:8766`. Os bots também enviam rostos e reações.
- **Latência:** os bots medem as idas e voltas. Na TV, um log de desenvolvimento `Social.lat` registra a ação e o frame.
- **Cada coreografia** tem um cenário próprio. Um jogo só entra no catálogo se o seu cenário passar.

---

## 7. Plano de testes [P]

| Nível | O quê | Como |
|---|---|---|
| Regras (por jogo) | Fases, vitória e derrota, empates, ações inválidas, mín. e máx. de jogadores, saída no meio, **destaques corretos** | JUnit em `:social-core`, determinístico |
| **Contrato (todos os jogos de graça)** | 1.000 partidas aleatórias por jogo com bots: nunca lança exceção, sempre termina, determinístico, `PhoneView` serializa, **nenhuma visão de p contém `secrets(s, p)`**, `mood` coerente (sem apresentador nem reação em `SECRET` e `FOCUS`) | `GameContractTest` parametrizado |
| Segredo estrutural | Máfia: noite com a mesma forma em todos os papéis. Lugar Secreto e Quem Sabia?: mesma estrutura de widget | Asserções sobre a forma do JSON |
| Noite | `Ceremony`: **todo jogador recebe ≥ 1 troféu**, determinístico, empates estáveis. `Host`: nenhuma fala repete na noite, variáveis sempre preenchidas, filtro de tom respeitado, tempo mínimo entre falas. Fichas somam certo | `CeremonyTest`, `HostTest`, `NightTest` |
| Sala e protocolo | Chave errada → 403. Token A nunca vê a visão de B. `aid` repetido aplicado uma vez. `phase` velha rejeitada. Long-poll acorda em ≤ 50 ms. 13º jogador vira plateia. Corpo acima do teto → 413. Rosto inválido ou grande → rejeitado. Rosto só é servido com token. `Host` estranho → 421. Limites de reação e de taxa. Reconexão pelo `device`. A pausa desloca os prazos | `RoomServerTest` com sockets reais |
| Match (regressão) | Rotas, JSON e fluxo idênticos. A Roleta se comporta igual com o `Reel` extraído | `MatchServerContractTest` (escrito antes) + `MatchRulesTest` |
| Conteúdo | JSON válido, volumes mínimos, sem duplicatas. Psicólogo com 2 exemplos por regra. Tribunal com exatamente um culpado (10.000 casos). Falas do apresentador com variáveis válidas | Testes que leem os assets como dados |
| Celular | iOS Safari 15+, Chrome Android, Samsung Internet, Firefox Android. 360×640, paisagem, fonte a 130%, economia do iOS, bloquear e desbloquear. **Selfie e rabisco em cada navegador. Pôster baixado e compartilhado** | Checklist manual por fase |
| TV (hardware) | Controle remoto, foco inicial, VOLTAR leva à pausa, Play/Pause, MENU pausa os prazos, protetor de tela não entra em 6 min, HOME e volta em menos de 60 s, Configurações pausa, `am force-stop` e reconexão automática | Roteiros com `adb` + `screencap` |
| Desempenho e resistência | Orçamentos da 6.2, 12 bots com rostos, 2 h alternando jogos, Wi-Fi desligado e religado | `tools/social-perf.sh` |
| **Diversão** | Metas de 0.8 calculadas do `NightLog` (primeira decisão, tempo morto, atenção no celular) + 2 playtests com ficha | `tools/social-fun-report.sh` + `docs/social/playtests/` |

Fica de fora de propósito: Robolectric e testes de UI em Compose. A UI é provada no aparelho com roteiro e a lógica na JVM.

---

## 8. Roadmap [P]

O esforço é relativo, para uma pessoa: P (1–2 dias) · M (3–5 dias) · G (1–2 semanas) · GG (3+ semanas). **Cada fase reserva cerca de 25% para polimento** e só termina quando passa pelo **portão de diversão** (0.8) e pelo de desempenho (6.2).

Mudanças em relação à sugestão inicial, com o motivo:
- **Quem é Mais Provável** entra na Fase 1 como esqueleto, porque valida o núcleo, os rostos e o palco com regras mínimas.
- **A Fase 2 entrega uma noite completa**: 5 jogos + apresentador + cerimônia. É o primeiro marco que já parece um programa de TV.
- **Sintonia de Casal e Frequência** sobem, porque cobrem o uso a dois.
- **Lugar Secreto** desce, porque compartilha W7 e botões com Quem Sabia? e Psicólogo.
- **Filme em 5 Pistas** sobe para a Fase 4, porque reusa o catálogo e é o diferencial do launcher.
- **Máfia** vai para junto do **Tribunal**, porque os dois precisam do narrador e são os mais complexos.

| Fase | Entregáveis | Depende de | Esforço | Riscos | Critérios de conclusão |
|---|---|---|---|---|---|
| **0 — Base e extração segura** | `tools/social-perf.sh` + base medida. `MatchServerContractTest` **antes**. Módulo `:social-core` com `LanServer`. `QrCode` compacto. `Reel` extraído da Roleta | — | P | Mudar o Match ou a Roleta sem querer | Testes do Match verdes. Match e Roleta testados na TV. QR < 10 KB. Base registrada |
| **1 — Palco, sala e o primeiro jogo** | `Room` completa (incluindo rostos, reações e plateia). `RoomServer`. Contrato e kit. Celular: casca, **selfie e rabisco**, **envelope**, W1–W4, W10, reações e reconexão. TV: botão "Social", **palco da sala** com rostos entrando, catálogo, ficha, **sistema Palco** (tokens, `Flip`, `Stamp`, `Tally`, `Spotlight`/Holofote, cortina, vinheta, kit de 14 sons), `keepScreenOn` e ciclo de vida. `GameContractTest`, bots, `NightLog` (telemetria). **Quem é Mais Provável** completo | 0 | G+ | Escopo do Palco | Testes de sala e protocolo. Contrato verde. Orçamentos atendidos na TV. **Portão de diversão de QEMP** (playtest com 6+ pessoas). Match sem regressão |
| **2 — Uma noite completa** | Infiltrados (+ Camaleão), Frequência, Sintonia de Casal, Duas Verdades. **Camada da noite:** fichas, Placar, **Apresentador** (~200 falas), **Cerimônia** (~20 troféus), **pôster da noite** no celular e **Surpreenda-me**. Componentes `Turns`, `HowTo`, W5, W6 | 1 | G+ | Volume de conteúdo e tom do apresentador | Cada jogo passa nos portões. Uma **noite de playtest de 2 h** (casal + grupo) terminando na cerimônia, com "mais uma noite?" respondido sim |
| **3 — Dedução falada** | Lugar Secreto (postais), Quem Sabia?, Psicólogo, Mistério (atmosfera, `Ambience`), Missões (modo ambiente + Dossiê). W7, `buttons` no `PhoneView` | 2 | G | Conteúdo original, ilustrações | Portões por jogo. Modo ambiente com Δ de frames = 0. Teste de forma do segredo |
| **4 — Catálogo, duplas e memória da casa** | Filme em 5 Pistas (pixel, máscara, "Assistir agora"). Dupla Secreta (+ Campanha). **Perfis, Recordes da casa e histórico do casal** | 1 (2 ajuda) | G | TMDB fora do ar, privacidade dos perfis | Teste da máscara. "Assistir" abre o título. "Esquecer rostos" funciona. Portões |
| **5 — Narrativos** | Máfia (noite uniforme, **Jornal da Vila**, reprise, treino). Tribunal (gerador, fichas criminais, **Objeção!**, veredito, treino) | 3 | G–GG | Clareza, solubilidade | Teste de solubilidade (10.000). Noite uniforme provada. 2 partidas reais completas por jogo e portões |
| **6 — Desarme a Bomba** | Caixa e módulos MVP, W9 com SVG, progressão, crachás, Bomba do dia, último segundo em câmera lenta, replay. Depois mais 4 módulos | 1 | GG | GPU, manual confuso | Orçamento da Bomba. ≥ 70% de vitória no nível 1 com pessoas novas. Teste regra ↔ manual por módulo. Portão |
| **Contínuo — Conteúdo, arte e som** | `CONTEUDO.md`, `ARTE.md`, `SOM.md`, pacotes por jogo, falas e troféus | por jogo | GG no total | Repetição, tom, direitos | Volumes mínimos. Revisão de originalidade por item |
| **Opcional — Match no Social** | `MatchGame` como 16º jogo | 1 | M | Paridade | `MatchRulesTest` passa contra o `MatchGame` |

Volumes mínimos (MVP → completo): Infiltrados 120 → 200 pares + 40 temas Camaleão · Lugar Secreto 24 → 36 locais × 7 papéis (+ postais) · Frequência 100 → 160 · Quem Sabia? 200 → 400 · Psicólogo 30 → 60 · Missões 90 → 180 · QEMP 150 → 300 · Duas Verdades 40 temas · Dupla Secreta 400 → 500 · Mistério 25 → 60 · Sintonia 150 → 300 · Tribunal 4 → 10 modelos · Apresentador 200 → 400 falas · Troféus 20 · Filme: dinâmico · Bomba: procedural.

---

## 9. Análise de riscos [P]

| Risco | Prob. | Impacto | Mitigação |
|---|---|---|---|
| Roteador com isolamento de clientes ou Wi-Fi de convidados | Média | Alto | URL e "mesmo Wi-Fi" na TV, página de erro explicativa, teste na rede real |
| iOS segura o long-poll em segundo plano | Alta | Médio | Contrato por foto completa, nova consulta no `visibilitychange`, "Reconectando…" |
| Processo do launcher morre com a Netflix aberta | Média | Médio | `room.json` + `device` devolvem os lugares. O jogo não é salvo |
| Protetor de tela ou espera da Sony no meio do jogo | **Confirmado** | Alto | `keepScreenOn` com a sala ativa e teste de 6 min |
| Jank nas coreografias | Média | Alto | 6 coreografias medidas uma vez, camadas assadas, animações finitas, cenário por jogo |
| **Privacidade dos rostos** | Média | Alto | Só na memória por padrão, consentimento explícito para lembrar, "Esquecer rostos", nunca saem da rede, servidos só com token. Rabisco e inicial como alternativa |
| **Tom do apresentador** (ofende, cansa) | Média | Alto | Falas curadas, tom Família por padrão, tempo mínimo entre falas, sem repetição, só em fases de conversa e "Silenciar apresentador" no menu |
| Escopo da camada da noite inchar | Média | Alto | Só depois dos primeiros 5 jogos (Fase 2). Fichas, falas e troféus são **dados** e não exigem código por jogo |
| NanoHTTPD 2.3.1 parada | Baixa | Médio | A superfície fica no `LanServer` e é trocável sem tocar a `Room` |
| Inundação ou corpos grandes | Baixa | Médio | Pool limitado, tetos por rota, limites de taxa e de reação, checagem do `Host` |
| Segredo vazando por toque, tempo ou som | Média | Alto | Envelope, noite uniforme, celular mudo, teste de forma |
| Leitura do tráfego na rede local | Baixa | Baixo | Risco aceito e documentado |
| Conteúdo insuficiente, repetitivo ou ofensivo | Alta | Alto | Guias, volumes mínimos, histórico, tags, picante desligado por padrão |
| Produção de arte e som (custo, consistência) | Alta | Médio | Estilo único e simples (vetor plano), glifos em vez de cenas, kit de 14 sons, fontes próprias ou CC0 |
| **Direitos:** marcas (Spyfall, Codenames, Wavelength, Insider, Black Stories, Undercover, One Night…) | Alta, se ignorado | Alto | **Nomes originais**, nenhuma referência no app nem no repositório público |
| **Direitos:** textos, ilustrações, música | Alta, se ignorado | Alto | Tudo **original** ou CC0, módulos e glifos próprios, geração procedural. Pela Lei 9.610/98, art. 8º, II, **regras de jogo não são protegidas**, mas textos, arte, música e nomes são. Se houver rascunho com IA, revisar e checar semelhança. O repositório é público (Apache-2.0) |
| Termos do TMDB no Filme em 5 Pistas | Baixa | Médio | Uso pessoal, atribuição já no README, cache como hoje |
| 15 jogos virarem um monólito | Média | Alto | Contrato + kit + coreografias, sem widget novo sem 2 jogos, ~400 linhas por arquivo, teste de contrato e portões obrigatórios |
| Acessibilidade (daltonismo, distância, timidez) | Média | Médio | Cor **e** forma, nada abaixo de 20 sp no jogo, modos escritos e Holofote opcional |

---

## 10. Checklist de implementação [P]

**Fase 0**
- [ ] Salvar este plano em `atv-home/docs/social/PLANO.md`.
- [ ] `tools/social-perf.sh` e base registrada em `docs/social/perf/`.
- [ ] `MatchServerContractTest` (`/`, `/deck`, `/join`, `/vote`, `/state`).
- [ ] Conferir na NanoHTTPD 2.3.1: `DefaultAsyncRunner` e `parseBody`.
- [ ] Criar `:social-core` com `LanServer` (teto por rota, `Host`, pool, `localIp`, assets injetados). `MatchServer` herda dele sem mudar nada.
- [ ] `QrCode` compacto no Match. `Reel` extraído com a Roleta inalterada. Medir o PSS do Match antes e depois.

**Fase 1**
- [ ] Ficha de design de QEMP aprovada.
- [ ] `Room`: chave, tokens, `device`, rostos (validação e teto), presença, plateia, trava, anfitrião, versão, long-poll com hash, prazos (`Scheduler` injetável), pausa, `aid`, `phase`, limites, reações, `room.json`.
- [ ] `RoomServer` + `Protocol` (2.4) e `RoomServerTest`.
- [ ] `Game<S>`, `GameInfo`, `PhoneView`, `Highlight`, `Mood`, kit `Votes`/`Scores`/`ContentDeck`, `GameContractTest`, `NightLog`.
- [ ] Celular: casca, selfie (`capture`) com redução no canvas, rabisco, envelope com lacre, W1–W4, W10, reações, reconexão, relógio por diferença. ≤ 45 KB com gzip.
- [ ] Palco: tokens `Stage.*`, avatar com rosto e forma, `Flip`, `Stamp`, `Tally`, `Spotlight` + Holofote, cortina, vinheta, `Sfx` com 14 sons (`SOM.md`) e cenário de GPU por coreografia.
- [ ] TV: `Screen.Social`, botão "Social", `SocialViewModel` (60 s, 15 min, `onStop`, `keepScreenOn`), palco da sala, catálogo, ficha e menu de pausa.
- [ ] QEMP completo (Aposta, Defenda-se, Unânime, dobro no fim, títulos) com 150 perguntas.
- [ ] Bots, latência, orçamentos e **playtest com ficha**.

**Fase 2**
- [ ] Fichas de design dos 4 jogos.
- [ ] `Turns`, `HowTo`, W5, W6.
- [ ] Infiltrados (+ Camaleão, última palavra, letra por letra) · Frequência (dial com cliques, tampa, Na mosca) · Sintonia de Casal (Defenda, termômetro) · Duas Verdades (polígrafo).
- [ ] `Night`: fichas e Placar da noite. `Host` com `host.json` (200 falas, 2 tons) e `HostTest`. `Ceremony` com `awards.json` (20 troféus) e `CeremonyTest`. Tela de cerimônia. Troféu e pôster no celular. Surpreenda-me.
- [ ] Noite de playtest de 2 h e relatório de diversão.

**Fase 3**
- [ ] W7, `buttons`, modo ambiente, `Ambience`.
- [ ] Lugar Secreto (24 postais, PROCURADO) · Quem Sabia? (`Spotlight`) · Psicólogo (diagnóstico) · Mistério (noir, máquina de escrever) · Missões (Dossiê).
- [ ] Teste de forma do segredo.

**Fase 4**
- [ ] Filme em 5 Pistas: pipeline `Catalog` → pistas, máscara com teste, iscas, pôster pixelado, pré-carregamento, fontes, "Assistir agora".
- [ ] Dupla Secreta: gerador de chave com teste, W8, grade no controle, Campanha com mapa e progresso salvo.
- [ ] `Profiles` (consentimento, "Esquecer rostos"), Recordes da casa (fileira no Social), histórico do casal.

**Fase 5**
- [ ] `HostRibbon` como narrador + `Tts` opcional. Máfia: papéis, noite uniforme (teste), Jornal da Vila (`Headline`), reprise, treino, modo iniciante.
- [ ] Tribunal: gerador com teste de solubilidade, mural assado, fichas criminais, Objeção!, veredito, treino, 4 casos.

**Fase 6**
- [ ] Caixa em cache, 7 segmentos, glifos compartilhados (`PathParser` ↔ SVG), W9.
- [ ] Fios, Botão, Símbolos, Labirinto (teste regra ↔ manual). Progressão, crachás, Bomba do dia, último segundo, replay, sons.
- [ ] Orçamento da Bomba e teste com pessoas novas.

**Transversal**
- [ ] `CONTEUDO.md`, `ARTE.md`, `SOM.md` e uma ficha de design por jogo em `docs/social/fichas/`.
- [ ] README: seção "Social" (como entrar, rede, privacidade dos rostos), créditos e nota de nomes originais.
- [ ] Commits em inglês no imperativo, um por item verificável.

---

## Verificação ponta a ponta

1. **Testes automáticos:** `./gradlew :social-core:test :app:testDebugUnitTest` (regras, contrato, noite, protocolo e regressão do Match).
2. **Build e instalação:** `./gradlew :app:assembleRelease && adb -s 192.168.1.3:5555 install -r app/build/outputs/apk/release/app-release.apk`.
3. **Desempenho:** `tools/social-perf.sh baseline` e `tools/social-perf.sh <jogo>` com os bots (`-Dsocial.tv=192.168.1.3:8766`), comparados com a 6.2.
4. **Diversão:** `tools/social-fun-report.sh` sobre o `NightLog` da sessão, comparado com 0.8, e a ficha de playtest em `docs/social/playtests/`.
5. **Hardware:** o roteiro de TV da seção 7.
6. **Celulares:** a matriz de navegadores, com selfie, envelope, reações e pôster, testada com pessoas reais.
7. **Match e Roleta:** uma partida real de cada depois de toda fase que toca `LanServer`, `QrCode` ou `Reel`.

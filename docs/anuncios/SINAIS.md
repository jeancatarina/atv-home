# Sem anúncios: o que cada app mostra durante um anúncio

A guarda (`app/src/main/java/dev/jean/atvhome/guard/`) não conhece nenhum streaming. Ela tem três
leitores e aprende sozinha, por app, qual deles enxerga o intervalo. Este arquivo é onde se anota o
que foi medido na TV, para ajustar as palavras de `app/src/main/assets/guard/rules.json` aos textos
reais e para dizer, sem enfeite, onde ela não enxerga nada.

**Estado:** ainda não medido. Em 2026-11-10 a TV (`192.168.1.3:5555`) não respondeu ao adb; as
palavras do `rules.json` são o ponto de partida, não o texto real de cada app.

## Como medir

1. `tools/ad-guard-setup.sh` uma vez depois de instalar.
2. Por app, durante um anúncio e durante o filme: `tools/ad-signals.sh <app>-ad` e `tools/ad-signals.sh <app>-film`.
3. Com o filme tocando, o teste do espelho: `adb shell screenrecord --size 1280x720 --time-limit 20 /sdcard/x.mp4`.
   É o pior caso do leitor 3 (espelho ligado o tempo todo e codificando vídeo). O filme engasgou? O
   arquivo (`adb pull /sdcard/x.mp4`) mostra a interface e o selo, ou sai preto?

| Coluna | Como responder |
|---|---|
| Leitor 1 | Em `media_session.txt`, no bloco do pacote: há `ADVERTISEMENT` na `metadata`? |
| Leitor 2 | Em `ui.xml`: há nó com o texto do selo ou do botão de pular? `text`, `content-desc`, `resource-id`, `bounds` |
| Leitor 3 | `screen.png` mostra o selo/contagem ou sai preto? Texto exato, idioma, canto (% da largura e altura) |
| Contagem | Entre `screen.png` e `screen-2s.png` o número caiu 2? |
| Espelho | O `screenrecord` engasgou o filme, baixou a qualidade ou fez o app parar com erro de proteção? A gravação mostra a interface? Se o app parar, marque-o: a guarda não pode usar o leitor 3 nele |
| Pular | Só YouTube: `adb shell input tap <x> <y>` no "Pular" pula? E `adb shell input keyevent 23`? |
| Mudo | `adb shell input keyevent 164` tira o som de fato (soundbar/eARC)? |

## Medições

| App (pacote) | Leitor 1 | Leitor 2 | Leitor 3 | Contagem | Espelho | Pular | Mudo | Resultado |
|---|---|---|---|---|---|---|---|---|
| Netflix `com.netflix.ninja` | | | | | | | | |
| Prime Video `com.amazon.amazonvideo.livingroom` | | | | | | | | |
| Disney+ `com.disney.disneyplus` | | | | | | | | |
| Max `com.wbd.stream` | | | | | | | | |
| YouTube `com.google.android.youtube.tv` | | | | | | | | |
| Globoplay `com.globo.globotv` | | | | | | | | |
| Apple TV `com.apple.atve.sony.appletv` (sem anúncio: a guarda nunca pode disparar) | | | | | | | | |

## Desempenho

A regra: com "Sem anúncios" ligado, a TV fica igual ou melhor, nunca pior. A TV tem 2,3 GB de RAM,
e nem o filme, nem a Home, nem os menus dos apps podem engasgar por causa da guarda. O desenho:

- A guarda roda no processo `dev.jean.atvhome:guard`; o `App.onCreate` sai cedo nele.
- O serviço de acessibilidade só fica na lista do sistema enquanto **outro app toca som**
  (`AdShield`: `AudioPlaybackCallback`, sem polling, mais `HomeActivity.onStart/onStop`). Com qualquer
  serviço desses ligado, o Compose de qualquer app (a Home inclusive) mantém a árvore de semântica a
  cada mudança; assim, Home e menus não pagam nada. Uma pausa só o tira depois de 10 s, para um
  intervalo não cair entre o anúncio e o filme. `accessibility_enabled` volta ao valor de antes.
- Teclas só passam pela guarda enquanto a cobertura está na tela.
- O espelho da tela fica sem superfície entre as olhadas; cada olhada pega um quadro só. Sem olhar
  por 30 s, o modelo do OCR, os bitmaps e a fila de imagens são liberados.
- Calmo, uma olhada a cada 2 s; em alerta, uma por segundo. OCR numa thread de segundo plano.
- `isMusicActive` antes de tudo; a árvore do app só é lida em app novo, app "de árvore" ou durante o intervalo.

Contra falso positivo (o pior que a guarda pode fazer é cobrir um filme): duas leituras para entrar;
palavra solta só fora do meio da tela e com até 4 textos à vista; contagem regressiva sem palavra só
num app que já teve anúncio confirmado; nunca mais de 4 min; qualquer tecla mostra a tela.

| O que | Limite | Como medir |
|---|---|---|
| Home, quadros | Diferença ≤ 2 pontos de jank entre desligado e ligado (meta do projeto: ≤ 10%, p90 ≤ 20 ms, p95 ≤ 33 ms) | `tools/ad-guard-perf.sh home home-off` e `home-on` |
| Processo do launcher | PSS no patamar de hoje (base 123 MB) | idem |
| Vídeo | Nenhum quadro perdido a mais, em 60 s | `tools/ad-guard-perf.sh video <app>-off` e `<app>-on` |
| Processo `:guard` | PSS ≤ 80 MB lendo a tela, ≤ 30 MB parado; CPU ≤ 15% de um núcleo em regime calmo | idem |
| Menus dos apps | Navegar no menu de um app (sem som tocando) igual com a guarda ligada e desligada | no olho, e `dumpsys accessibility` não lista a guarda |

Se estourar, nesta ordem, uma coisa por vez: `SLOW_EVERY` 2 → 3 (`AdGuardService`); captura em
960×540 (`W`, `H`, `TOP`, `BOTTOM` de `ScreenService`); `KEEP_MIRROR = true` (`ScreenService`, só se
ligar e desligar o espelho for o que engasga); `"screen_reader": false` em `rules.json`.

## Onde nenhum leitor enxerga

(preencher depois das medições)

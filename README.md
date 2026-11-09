# Início — tela inicial estilo Apple TV para Android TV

Um launcher (tela inicial) para Android TV / Google TV com a cara da Apple TV e tudo o que ajuda a
**decidir o que assistir**: notas do IMDb, onde cada título está no Brasil, "O que assistir agora?",
busca em português do jeito que a gente fala e avisos quando algo da sua lista chega a um streaming seu.

Feito e testado numa Sony BRAVIA (Android 10, 1080p, 2,3 GB de RAM). Gratuito e de código aberto.

![Tela inicial: o topo mostra o que está em alta no app focado](docs/img/inicio.jpg)

## Fotos

| | |
|---|---|
| ![Página do título com IMDb, elenco e parecidos](docs/img/titulo.jpg) | ![Filmografia de um ator com o app onde cada filme está](docs/img/filmografia.jpg) |
| **Página do título**: IMDb com votos, onde assistir, elenco e parecidos | **Filmografia**: o selo mostra em qual dos seus apps cada filme está |
| ![O que assistir agora?](docs/img/o-que-assistir.jpg) | ![Roleta de filmes e séries](docs/img/roleta.jpg) |
| **O que assistir agora?**: tempo + clima → três sugestões | **Roleta**: três títulos sorteados com IMDb 6,5+ |
| ![Match: QR code na TV](docs/img/match-qr.jpg) | ![Match: deu match!](docs/img/match.jpg) |
| **Match**: cada um entra pelo QR code no celular | **Deu match!**: o título que todos curtiram |
| ![Trailer tocando no fundo](docs/img/trailer.jpg) | ![Busca em português](docs/img/busca.jpg) |
| **Trailer no fundo**, sem som (🔇) | **Busca**: "comédia curta na Netflix com nota alta" |
| ![Explorar com filtros](docs/img/explorar.jpg) | ![Central com as entradas HDMI](docs/img/central.jpg) |
| **Explorar**: filtros por app, gênero, nota e época | **Central** (tecla MENU): entradas HDMI e ajustes |
| ![Protetor de tela com os aéreos da Apple](docs/img/protetor.jpg) | |
| **Protetor de tela** com os aéreos da Apple | |

---

## O que ele faz

| | |
|---|---|
| **Top Shelf em tela cheia** | Ao focar a Netflix, o topo mostra o que está em alta **na Netflix Brasil**; o mesmo para Prime Video, Disney+, Max, Globoplay, Apple TV e Claro tv+. Logotipo do título, nota do IMDb, classificação, duração, gêneros e sinopse. |
| **Assistir direto no título** | "Assistir no Netflix" abre a **página da série/filme** dentro do app (Netflix, Prime Video, Disney+, Apple TV), usando os códigos abertos do Wikidata. Globoplay e Max abrem já na busca pelo nome. O Claro tv+ vem com os links desligados de fábrica: ele abre no início e o Início avisa o nome para procurar. |
| **Trocar o título na mão** | Do app, **▲** seleciona o título do topo; aí **◀ ▶** passam para o anterior/próximo e **OK** assiste. As teclas ◀◀ ▶▶ do controle também trocam. |
| **Trailer no fundo** | Parou num título, em poucos segundos o trailer começa a tocar sem som atrás do texto, como na Apple TV, e depois de ~30 s volta para a imagem e segue. **Play/Pause** no controle liga o som. |
| **Match pelo celular** | A TV mostra um QR code; cada pessoa abre no celular (sem instalar nada), desliza os pôsteres como no Tinder, e o primeiro título que **a sala inteira** curtir aparece na TV com "Deu match!". Funciona com quantas pessoas quiserem: se ninguém concordar até o fim do baralho, aparece **o mais votado** ("7 de 10 curtiram"). "Outro match" segue para o próximo sem perder os votos. Tudo na sua rede Wi-Fi, sem nuvem. |
| **Esta semana nos seus streamings** | Séries com episódio novo nos próximos dias e filmes que acabaram de chegar aos seus apps: "Novo ep. quinta · Netflix", "Novo no Prime Video". |
| **Verde da casa** | Telas e botões levam um verde-oliva escuro (`Accent.Wall` em `ui/theme/Accent.kt`; troque a cor ali para combinar com a sua sala). |
| **Roleta** | Escolha **Filme** ou **Série** e gire: três rolos estilo caça-níquel param em três títulos dos seus streamings (de apps diferentes), sempre com IMDb 6,5 ou mais. |
| **Fileira de apps com o foco do tvOS** | O cartão cresce, inclina na direção do controle e recebe um brilho. Segure OK para reorganizar: ◀ ▶ move, ▼ oculta, OK conclui. |
| **O que assistir agora?** | Duas perguntas (quanto tempo você tem e o que está a fim) e três sugestões dos **seus** streamings, todas com IMDb 7 ou mais. |
| **Busca por voz e em português** | "comédia curta na Netflix com nota alta", "suspense dos anos 90", "série de terror na max", "filmes com o Wagner Moura". Ele mostra o que entendeu: *Filmes · Comédia · Netflix · até 1 h 40 min · IMDb ≥ 7,5*. |
| **Explorar** | Filtros de filme/série, streaming, gênero, nota mínima no IMDb, época e duração, ordenados pela nota do IMDb. |
| **Página do título** | IMDb com número de votos, onde assistir no Brasil, trailer, elenco e parecidos. Clicar num ator mostra a filmografia com o selo do app onde você já pode ver cada filme. |
| **Minha Lista com avisos** | Uma checagem diária marca "Agora no Prime" quando o título chega a um streaming que você tem, e "Novo ep. sexta" em séries. |
| **Central (tecla MENU)** | Entradas HDMI com os nomes que você deu na TV, protetor de tela, Wi-Fi e configurações. |
| **Protetor de tela** | Já vem no Início: os vídeos aéreos da Apple, sem nenhum texto por cima, e um painel verde-escuro com a hora, a data e o **clima da semana** (Open-Meteo, cidade descoberta pela internet, sem conta). |

---

## Passo a passo para instalar

Você vai precisar de: um computador (macOS, Linux ou Windows) na mesma rede Wi-Fi da TV, e uns 20 minutos.

### 1. Ligue a depuração na TV (ADB)

1. Na TV: **Configurações → Preferências do dispositivo → Sobre**.
2. Desça até **Versão** (ou "Build") e aperte **OK 7 vezes**, até aparecer "Você é um desenvolvedor".
3. Volte e entre em **Preferências do dispositivo → Opções do desenvolvedor**.
4. Ligue **Depuração USB** e, se existir, **Depuração por rede / ADB pela rede**.
5. Anote o **endereço IP** da TV: **Configurações → Rede e Internet → (sua rede)**. Exemplo: `192.168.1.3`.

### 2. Instale as ferramentas no computador

- **ADB** (para falar com a TV): [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools).
  No macOS com Homebrew: `brew install --cask android-platform-tools`.
- **Java 17**: [Temurin 17](https://adoptium.net/temurin/releases/?version=17).
- **Android SDK** (só a linha de comando, não precisa do Android Studio):
  [Command line tools](https://developer.android.com/studio#command-line-tools-only). Depois de descompactar:

  ```sh
  sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
  ```

  Quem já tem o Android Studio pode pular esta parte.

Conecte na TV (troque pelo IP dela). Na primeira vez, a TV pergunta se autoriza o computador: escolha **Sempre permitir** e OK.

```sh
adb connect 192.168.1.3:5555
adb devices          # deve listar a TV como "device"
```

### 3. Crie a sua chave do TMDB (grátis, 3 minutos)

> **E a chave do IMDb?** Não precisa. As notas do IMDb vêm do
> [arquivo público e gratuito do IMDb](https://developer.imdb.com/non-commercial-datasets/), que o app
> baixa sozinho todo dia. A única chave necessária é a do **TMDB**, de onde vêm capas, sinopses,
> elenco, trailers e "onde assistir".

1. Crie uma conta em **[themoviedb.org/signup](https://www.themoviedb.org/signup)** e confirme o e-mail.
2. Já logado, abra **[themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)**
   (Avatar → **Configurações** → **API**).
3. Clique em **Criar** / **Request an API Key** e escolha **Developer**.
4. Aceite os termos e preencha o formulário. Para uso pessoal vale:
   - *Type of use*: **Personal**
   - *Application name*: `Início TV`
   - *Application URL*: `http://localhost`
   - *Application summary*: `Tela inicial pessoal para minha TV`
   - Endereço e telefone: os seus.
5. Na página de API vão aparecer duas coisas. **Copie a do meio, chamada "API Read Access Token"**
   (um texto longo que começa com `eyJ`). Ela é a que o app usa. A "API Key" curta não serve aqui.

Guarde esse token como uma senha: não publique nem mande em mensagens.

### 4. Baixe o projeto e coloque a chave

```sh
git clone https://github.com/jeancatarina/atv-home.git
cd atv-home
```

Crie um arquivo chamado **`local.properties`** na pasta do projeto com o caminho do SDK e o token:

```properties
sdk.dir=/caminho/para/o/android-sdk
tmdb.token=eyJhbGciOiJIUzI1NiJ9....(o seu token inteiro)
```

- macOS com Homebrew: `sdk.dir=/usr/local/share/android-commandlinetools` (Intel) ou
  `/opt/homebrew/share/android-commandlinetools` (Apple Silicon).
- Android Studio: geralmente `~/Library/Android/sdk` (macOS) ou `C:\\Users\\VOCÊ\\AppData\\Local\\Android\\Sdk` (Windows).

O `local.properties` já está no `.gitignore`: o token nunca vai para o GitHub.

### 5. Compile e instale na TV

```sh
./gradlew :app:assembleRelease          # no Windows: gradlew.bat :app:assembleRelease
adb -s 192.168.1.3:5555 install -r app/build/outputs/apk/release/app-release.apk
```

### 6. Use como tela inicial

Aperte **HOME** no controle. A TV pergunta qual tela inicial usar: escolha **Início** e depois **Sempre**.

Se você tinha outro launcher (Projectivy, por exemplo) e a pergunta continuar voltando, desative o
outro (não apaga nada, e volta com `pm enable`):

```sh
adb shell pm disable-user --user 0 com.spocky.projengmenu
```

### 7. Protetor de tela

O protetor com os aéreos da Apple e o clima da semana já vem no Início. Ative em
**Configurações → Preferências do dispositivo → Protetor de tela → Início · Aéreos**, ou:

```sh
adb shell settings put secure screensaver_components dev.jean.atvhome/.saver.AerialSaver
```

---

## Como usar

- **◀ ▶** na fileira de apps troca o Top Shelf para o app focado.
- **▲** a partir de um app seleciona o título do topo: **◀ ▶** trocam o filme, **OK** assiste no app.
- **▼** do título chega aos botões (Assistir, Detalhes, Trailer, Minha Lista). Lá em cima ficam
  **Buscar**, **O que assistir agora?**, **Roleta** e **Explorar**.
- **Segurar OK** num app: modo de edição (◀ ▶ mover, ▼ ocultar, OK concluir). Apps ocultos ficam no fim da tela; segure OK para mostrar de novo.
- **MENU**: Central com as entradas HDMI. **VOLTAR** fecha qualquer tela.

## Voltar ao launcher anterior

```sh
adb shell pm enable com.spocky.projengmenu      # se você desativou
adb shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity
```

Ou desinstale: `adb uninstall dev.jean.atvhome`.

## Problemas comuns

| Sintoma | Solução |
|---|---|
| "Sem conexão com o TMDB" na tela inicial | O token está faltando ou errado no `local.properties`. Confira se copiou o **API Read Access Token** inteiro e compile de novo. |
| Notas do IMDb não aparecem na primeira vez | O arquivo do IMDb (uns 9 MB) baixa na primeira abertura; leva alguns segundos. |
| "Assistir no…" abre o app mas não o título | Aquele título ainda não tem o código do serviço no Wikidata. O app avisa o nome para procurar. Dá para ajudar adicionando o código em [wikidata.org](https://www.wikidata.org). |
| `adb devices` mostra "unauthorized" | Aceite o pedido na tela da TV ("Sempre permitir"). |
| A pergunta "Usar … como Página inicial" volta sempre | Desative o outro launcher (passo 6). |
| Não aparece "Continuar assistindo" | Em muitas TVs o Android só deixa os launchers do fabricante lerem essa fileira dos outros apps. |

## Para desenvolver

```sh
./gradlew :app:testDebugUnitTest      # testes (leitor do IMDb, formatação, busca em português, apps ↔ streamings)
```

Kotlin, Jetpack Compose for TV, Coil, OkHttp e kotlinx.serialization. A tela tem 960×540 dp em
1080p. O desenho foi medido na TV: sombras borradas e degradês em tela cheia custam caro na GPU de TVs
(o degradê do Top Shelf é aplicado uma vez na imagem, em `Scrim.kt`).

## Créditos e licenças

- Código deste projeto: [Apache License 2.0](LICENSE).
- [Sofadrop](https://github.com/rfmarcos/projectivy-plugin-sofadrop) (Apache-2.0): base do leitor das notas do IMDb (`ImdbRatings.kt`).
- [Inter](https://github.com/rsms/inter) (SIL OFL 1.1, `licenses/Inter-OFL.txt`): a fonte, no lugar da SF Pro.
- [Aerial Views](https://github.com/theothernt/AerialViews): a lista dos vídeos aéreos da Apple (`assets/aerials.txt`) vem do trabalho deles.
- [Open-Meteo](https://open-meteo.com) (CC BY 4.0) e [ipwho.is](https://ipwho.is): clima e cidade, sem chave.
- [android-youtube-player](https://github.com/PierfrancescoSoffritti/android-youtube-player) (Apache-2.0): trailer no fundo, pelo player oficial do YouTube.
- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) (BSD-3) e [ZXing](https://github.com/zxing/zxing) (Apache-2.0): servidor e QR code do Match.
- Dados: [TMDB](https://www.themoviedb.org) (este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB),
  [IMDb Non-Commercial Datasets](https://developer.imdb.com/non-commercial-datasets/) (uso pessoal e não comercial)
  e [Wikidata](https://www.wikidata.org) (CC0).
- Apple TV, Netflix e as demais marcas pertencem aos seus donos; este projeto não tem relação com eles.

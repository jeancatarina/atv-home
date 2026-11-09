# Início

Launcher para Android TV no estilo da Apple TV, feito para uma Sony BRAVIA (Android 10, 1080p).
Kotlin + Jetpack Compose for TV. Tudo gratuito; dados do TMDB e notas do IMDb.

## O que tem

- **Top Shelf em tela cheia**: ao focar a Netflix, o topo mostra o que está em alta *na Netflix
  Brasil* (vale para Prime, Disney+, Max, Globoplay, Apple TV, Claro tv+). Logotipo do título,
  IMDb, classificação, duração, gêneros, sinopse, botões Assistir no app, Detalhes, Trailer e
  Minha Lista.
- **Fileira de apps** com o foco do tvOS (cresce, inclina na direção do controle, brilho
  especular). Segurar OK entra no modo de edição: ◀ ▶ mover, ▼ ocultar, OK concluir.
- **O que assistir agora?**: tempo disponível + clima → 3 sugestões dos seus streamings com IMDb 7+.
- **Buscar** com voz e linguagem natural: "comédia curta na Netflix com nota alta",
  "suspense dos anos 90", "filmes com o Wagner Moura".
- **Explorar**: filtros de tipo, streaming, gênero, nota mínima do IMDb, época e duração.
- **Página do título**: IMDb com votos, onde assistir no Brasil, elenco (abre a filmografia com
  selo de onde já dá para ver), parecidos.
- **Minha Lista** com avisos diários: "Agora no Prime" quando chega a um streaming seu, "Novo ep. sexta".
- **Central** na tecla MENU: entradas HDMI pelos nomes da TV, protetor de tela, Wi-Fi, configurações.

## Construir e instalar

```sh
. ~/.local/dl/env.sh                      # JDK 17, Android SDK, Gradle
echo "tmdb.token=SEU_TOKEN_DE_LEITURA" >> local.properties   # fora do git
./gradlew :app:testDebugUnitTest :app:assembleRelease
adb -s 192.168.1.3:5555 install -r app/build/outputs/apk/release/app-release.apk
```

Voltar ao launcher anterior: Configurações → Apps → Página inicial, ou
`adb shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity`.

## Código aberto usado

- [Sofadrop](https://github.com/rfmarcos/projectivy-plugin-sofadrop) (Apache-2.0): leitor do dataset
  de notas do IMDb (`ImdbRatings.kt`, com votos acrescentados).
- [Inter](https://github.com/rsms/inter) (OFL, `licenses/Inter-OFL.txt`): fonte, no lugar da SF Pro.
- [Aerial Views](https://github.com/theothernt/AerialViews): protetor de tela com os aéreos da Apple.
- Dados: [TMDB](https://www.themoviedb.org) e o dataset não comercial do [IMDb](https://developer.imdb.com/non-commercial-datasets/).

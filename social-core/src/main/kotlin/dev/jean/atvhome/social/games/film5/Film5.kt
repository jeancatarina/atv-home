package dev.jean.atvhome.social.games.film5

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Text
import kotlinx.serialization.json.jsonPrimitive

enum class FiStage { CLUE, REVEAL, DONE }

/** A film as the app's catalogue gives it: key|title|year|genres|minutes|tagline|actors|character|overview|poster|where. */
data class Film(
    val key: String, val title: String, val year: String, val genres: String, val minutes: String, val tagline: String,
    val actors: String, val character: String, val overview: String, val poster: String, val where: String,
) {
    companion object {
        fun parse(line: String): Film? = line.split("|").let { f ->
            if (f.size < 9 || f[1].isBlank()) null else Film(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f.getOrElse(9) { "" }, f.getOrElse(10) { "" })
        }
    }
}

data class FilmState(
    val players: List<Player>,
    val films: List<Film>,
    /** For each film the six titles on offer (the right one among decoys), in the order the phones show them. */
    val options: List<List<String>>,
    val round: Int = 0,
    val clue: Int = 0,
    /** Who answered this film: person → (clue number when they answered, option index). */
    val answers: Map<PlayerId, Pair<Int, Int>> = emptyMap(),
    val scores: Map<PlayerId, Int> = emptyMap(),
    val best: Map<PlayerId, Int> = emptyMap(),
    val stage: FiStage = FiStage.CLUE,
    val deadline: Long? = null,
) {
    val film: Film get() = films[round]
    val opts: List<String> get() = options[round]
    val rightIndex: Int get() = opts.indexOf(film.title)
    /** The clue texts shown so far (1 to 5). */
    fun clues(): List<String> = Film5.clueTexts(film).take(clue + 1)
}

/**
 * Filme em 5 Pistas: a secret film and clues that get easier. Everybody may answer once per film,
 * whenever they like: answering on clue k pays 6 − k points, a wrong answer pays nothing. Each
 * answer is a gamble on "now, or wait for an easier clue?". The films come from the launcher's own
 * catalogue (TMDB), so the game can end with "watch it now" on a service the household has.
 */
object Film5 : Game<FilmState> {
    private const val CLUE_MS = 15_000L
    private const val REVEAL_MS = 9_000L
    private val FILMS = listOf(3, 5, 8)
    const val OPTIONS = 6

    override val info = GameInfo(
        id = "film5", name = "Filme em 5 Pistas", tagline = "Quanto antes acertar, mais pontos",
        category = Category.DUO, minPlayers = 2, maxPlayers = 6, idealMin = 2, idealMax = 5, minutes = 12, difficulty = 1,
        style = "Um toque por filme, quando você arriscar",
        howTo = listOf(
            "A TV solta uma pista a cada 15 s: do ano ao título com letras escondidas. O pôster vai ficando nítido",
            "Achou que sabe? Escolhe o filme entre as 6 opções no celular. Só dá pra responder uma vez",
            "Na pista 1 vale 5 pontos, na 5 vale 1. Errou, perdeu",
        ),
        settings = listOf(Setting("films", "Filmes", FILMS.map { "$it" }, default = 1)),
    )

    override fun unavailable(content: List<String>): String? =
        if (content.mapNotNull(Film::parse).size < OPTIONS) "Preciso de internet pra buscar os filmes. Dá uma olhada no Wi-Fi da TV" else null

    /** Words of the title (3+ letters), so clues can hide them. */
    private fun titleWords(title: String) = title.split(" ", "-", ":", ",").map { Text.norm(it) }.filter { it.length >= 3 }.toSet()

    /** [text] with every word of [title] replaced by ▒▒▒ (accent and case insensitive). */
    fun mask(text: String, title: String): String {
        val hide = titleWords(title)
        return Regex("[\\p{L}\\p{N}]+").replace(text) { m -> if (Text.norm(m.value) in hide) "▒▒▒" else m.value }
    }

    fun clueTexts(f: Film): List<String> {
        val tag = f.tagline.ifBlank { f.overview.take(90) }
        val letters = f.title.split(" ").joinToString("   ") { w -> w.take(1) + " " + "_ ".repeat((w.length - 1).coerceAtLeast(0)).trim() }
        return listOf(
            "Lançado em ${f.year} · ${f.genres} · ${f.minutes} min",
            "“${mask(tag, f.title)}”",
            "No elenco: ${f.actors.split(",").take(2).joinToString(" e ") { it.trim() }.ifBlank { "elenco desconhecido" }}",
            (if (f.character.isNotBlank()) "Personagem: ${mask(f.character, f.title)}. " else "") + mask(f.overview.take(150), f.title) + "…",
            "O título: $letters",
        )
    }

    override fun setup(ctx: Setup): FilmState {
        val all = ctx.content.mapNotNull(Film::parse)
        val n = FILMS[(ctx.settings["films"] ?: 1).coerceIn(0, FILMS.lastIndex)].coerceAtMost(all.size)
        val films = all.shuffled(ctx.random).take(n)
        val options = films.map { f ->
            (all.filter { it.title != f.title }.shuffled(ctx.random).take(OPTIONS - 1).map { it.title } + f.title).shuffled(ctx.random)
        }
        return FilmState(ctx.players, films, options, deadline = ctx.now + CLUE_MS)
    }

    private fun reveal(s: FilmState, now: Long): FilmState {
        var scores = s.scores; var best = s.best
        s.answers.forEach { (p, a) ->
            if (a.second == s.rightIndex) {
                scores = scores + (p to (scores[p] ?: 0) + (6 - a.first))
                best = best + (p to minOf(best[p] ?: 9, a.first))
            }
        }
        return s.copy(stage = FiStage.REVEAL, scores = scores, best = best, deadline = now + REVEAL_MS)
    }

    private fun next(s: FilmState, now: Long) =
        if (s.round + 1 >= s.films.size) s.copy(stage = FiStage.DONE, deadline = null)
        else s.copy(round = s.round + 1, clue = 0, answers = emptyMap(), stage = FiStage.CLUE, deadline = now + CLUE_MS)

    override fun act(s: FilmState, by: PlayerId, a: Action, now: Long): Step<FilmState> {
        if (s.stage != FiStage.CLUE) return Step.Rejected("Esse filme já foi revelado")
        if (by in s.answers) return Step.Rejected("Você já respondeu esse filme")
        val idx = a.data["key"]?.jsonPrimitive?.content?.toIntOrNull()
        if (a.type != Act.CHOOSE || idx == null || idx !in s.opts.indices) return Step.Rejected("Escolhe um filme")
        val n = s.copy(answers = s.answers + (by to (s.clue + 1 to idx)))
        return Step.Ok(if (n.answers.size >= s.players.size) reveal(n, now) else n)
    }

    override fun host(s: FilmState, cmd: HostCmd, now: Long): FilmState = when (s.stage) {
        FiStage.CLUE -> if (s.clue >= 4 || cmd == HostCmd.SKIP) reveal(s, now) else s.copy(clue = s.clue + 1, deadline = now + CLUE_MS)
        FiStage.REVEAL -> next(s, now)
        FiStage.DONE -> s
    }

    override fun timeout(s: FilmState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: FilmState) = when (s.stage) {
        FiStage.CLUE -> TvAction(if (s.clue >= 4) "Revelar o filme" else "Próxima pista")
        FiStage.REVEAL -> TvAction("Próximo filme")
        FiStage.DONE -> null
    }

    override fun deadline(s: FilmState) = s.deadline
    override fun phase(s: FilmState) = "${s.stage.name.lowercase()}:${s.round}"
    override fun mood(s: FilmState) = if (s.stage == FiStage.CLUE) Mood.FOCUS else Mood.TALK

    override fun phone(s: FilmState, p: PlayerId): PhoneView {
        val ph = phase(s)
        return when (s.stage) {
            FiStage.CLUE -> if (p in s.answers) PhoneView(ph, "Respondido ✓", body = Widget.Wait("Espera o pessoal", "Olha pra TV"), endsAt = s.deadline)
            else PhoneView(
                ph, "Filme ${s.round + 1} de ${s.films.size} · vale ${5 - s.clue} pontos agora",
                body = Widget.Choose("Qual é o filme? (uma resposta só)", s.opts.mapIndexed { i, t -> Option("$i", "${'A' + i} · $t") }, null), endsAt = s.deadline,
            )
            FiStage.REVEAL -> PhoneView(ph, body = Widget.Wait("Olha pra TV", react = true))
            FiStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: FilmState, p: PlayerId, now: Long): FilmState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, answers = s.answers - p)
        return when {
            s.stage == FiStage.DONE -> rest
            rest.players.size < 2 -> rest.copy(stage = FiStage.DONE, deadline = null)
            rest.stage == FiStage.CLUE && rest.answers.size >= rest.players.size -> reveal(rest, now)
            else -> rest
        }
    }

    override fun over(s: FilmState): Outcome? {
        if (s.stage != FiStage.DONE) return null
        val top = s.scores.maxByOrNull { it.value }
        val name = s.players.firstOrNull { it.id == top?.key }?.name
        return Outcome(
            s.scores.filterValues { it > 0 },
            if (top != null && top.value > 0 && name != null) "$name sabe tudo de cinema: ${top.value} pontos" else "Ninguém acertou nenhum filme",
            s.best.filterValues { it <= 2 }.map { (who, k) -> Highlight("CINEPHILE", who, k) },
        )
    }

    override fun secrets(s: FilmState, p: PlayerId): Set<String> = emptySet()
}

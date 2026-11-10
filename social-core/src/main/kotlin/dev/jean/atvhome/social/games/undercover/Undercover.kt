package dev.jean.atvhome.social.games.undercover

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Text
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class Role { CIVIL, INFILTRADO, BRANCO }
enum class Side { CIVIS, INFILTRADOS, BRANCO }
enum class UStage { WORDS, CLUES, VOTE, ELIM, GUESS, DONE }

data class UndercoverState(
    val players: List<Player>,
    val roles: Map<PlayerId, Role>,
    val civilWord: String,
    val infiltratorWord: String,
    val alive: List<PlayerId>,
    val stage: UStage,
    val round: Int = 1,
    val speaker: Int = 0,
    val ready: Set<PlayerId> = emptySet(),
    val votes: Votes<PlayerId> = Votes(emptySet()),
    val candidates: List<PlayerId> = emptyList(),
    val revote: Boolean = false,
    val deadline: Long? = null,
    val eliminated: PlayerId? = null,
    val guess: String? = null,
    val winner: Side? = null,
) {
    fun name(id: PlayerId) = players.firstOrNull { it.id == id }?.name ?: "?"
    val speakerId: PlayerId? get() = alive.getOrNull(speaker)
    fun wordOf(p: PlayerId): String? = when (roles[p]) {
        Role.CIVIL -> civilWord
        Role.INFILTRADO -> infiltratorWord
        else -> null
    }
}

/**
 * Infiltrados: civilians and infiltrators get two similar words and don't know which side they are
 * on; the Sr. Branco gets none. Everyone gives one spoken clue, then the room votes someone out.
 * A Sr. Branco who is voted out can still win by guessing the civilians' word.
 */
object Undercover : Game<UndercoverState> {

    private const val WORDS_MS = 60_000L
    private const val CLUE_MS = 12_000L
    private const val VOTE_MS = 40_000L
    private const val REVOTE_MS = 20_000L
    private const val ELIM_MS = 8_000L
    private const val GUESS_MS = 25_000L
    private const val MAX_ROUNDS = 8

    override val info = GameInfo(
        id = "undercover", name = "Infiltrados", tagline = "Todo mundo tem uma palavra. Quase todo mundo.",
        category = Category.DEDUCTION, minPlayers = 4, maxPlayers = 10, idealMin = 5, idealMax = 8, minutes = 14, difficulty = 2,
        style = "Segura pra ver, um voto por rodada",
        howTo = listOf(
            "Cada um vê a sua palavra no celular. Os infiltrados têm uma parecida, e o Sr. Branco não tem nenhuma",
            "Cada um na sua vez fala uma pista em voz alta, sem dizer a palavra",
            "A sala vota em quem sai. Os civis ganham se pegarem todos os infiltrados, e os infiltrados ganham se sobrarem em número igual",
        ),
        settings = listOf(Setting("white", "Sr. Branco", listOf("Não", "Sim"), default = 1)),
    )

    override fun setup(ctx: Setup): UndercoverState {
        val n = ctx.players.size
        val white = (ctx.settings["white"] ?: 1) == 1 && n >= 5
        val infiltrators = when { n <= 6 -> 1; n <= 8 -> 2; else -> 3 }
        val order = ctx.players.map { it.id }.shuffled(ctx.random)
        val roles = HashMap<PlayerId, Role>()
        order.forEachIndexed { i, id ->
            roles[id] = when {
                i < infiltrators -> Role.INFILTRADO
                white && i == infiltrators -> Role.BRANCO
                else -> Role.CIVIL
            }
        }
        val pair = (ctx.content.ifEmpty { listOf("Gato|Cachorro") }).random(ctx.random).split("|")
        val flip = ctx.random.nextBoolean()
        return UndercoverState(
            ctx.players, roles, civilWord = if (flip) pair[1] else pair[0], infiltratorWord = if (flip) pair[0] else pair[1],
            alive = ctx.players.map { it.id }, stage = UStage.WORDS, deadline = ctx.now + WORDS_MS,
        )
    }

    // --- Transitions -------------------------------------------------------------------------------

    private fun clues(s: UndercoverState, now: Long): UndercoverState {
        // The Sr. Branco never speaks first: it would give him away or make him guess blind
        val rotated = s.alive.let { it.drop((s.round - 1) % it.size) + it.take((s.round - 1) % it.size) }
        val order = if (s.roles[rotated.first()] == Role.BRANCO && rotated.size > 1) rotated.drop(1) + rotated.first() else rotated
        return s.copy(stage = UStage.CLUES, alive = order, speaker = 0, deadline = now + CLUE_MS, eliminated = null)
    }

    private fun nextSpeaker(s: UndercoverState, now: Long): UndercoverState =
        if (s.speaker + 1 >= s.alive.size) startVote(s, s.alive, false, now)
        else s.copy(speaker = s.speaker + 1, deadline = now + CLUE_MS)

    private fun startVote(s: UndercoverState, candidates: List<PlayerId>, revote: Boolean, now: Long) = s.copy(
        stage = UStage.VOTE, candidates = candidates, revote = revote,
        votes = Votes(s.alive.toSet()), deadline = now + if (revote) REVOTE_MS else VOTE_MS,
    )

    private fun resolveVote(s: UndercoverState, now: Long): UndercoverState {
        val leaders = s.votes.leaders()
        return when {
            leaders.size == 1 -> eliminate(s, leaders.single(), now)
            !s.revote && leaders.size > 1 -> startVote(s, leaders, true, now)
            else -> s.copy(stage = UStage.ELIM, eliminated = null, deadline = now + ELIM_MS)
        }
    }

    private fun eliminate(s: UndercoverState, who: PlayerId, now: Long) =
        s.copy(stage = UStage.ELIM, eliminated = who, alive = s.alive - who, deadline = now + ELIM_MS)

    private fun afterElimination(s: UndercoverState, now: Long): UndercoverState {
        val out = s.eliminated
        if (out != null && s.roles[out] == Role.BRANCO && s.guess == null) return s.copy(stage = UStage.GUESS, deadline = now + GUESS_MS)
        return verdict(s, now)
    }

    private fun verdict(s: UndercoverState, now: Long): UndercoverState {
        val civils = s.alive.count { s.roles[it] == Role.CIVIL }
        val bad = s.alive.size - civils
        val winner = when {
            bad == 0 -> Side.CIVIS
            bad >= civils || s.round >= MAX_ROUNDS -> Side.INFILTRADOS
            else -> null
        }
        return if (winner != null) s.copy(stage = UStage.DONE, winner = winner, deadline = null)
        else clues(s.copy(round = s.round + 1, guess = null), now)
    }

    // --- Game interface ------------------------------------------------------------------------------

    override fun act(s: UndercoverState, by: PlayerId, a: Action, now: Long): Step<UndercoverState> {
        val key = a.data["key"]?.jsonPrimitive?.content
        return when (s.stage) {
            UStage.WORDS -> {
                if (a.type != Act.BUTTON || key != "ready") return Step.Rejected("Toca em “Já vi”")
                val next = s.copy(ready = s.ready + by)
                Step.Ok(if (next.ready.containsAll(s.alive)) clues(next, now) else next)
            }
            UStage.CLUES -> {
                if (a.type != Act.BUTTON || key != "done" || by != s.speakerId) return Step.Rejected("Não é a sua vez")
                Step.Ok(nextSpeaker(s, now))
            }
            UStage.VOTE -> {
                val target = a.data["id"]?.jsonPrimitive?.content
                if (a.type != Act.PICK || by !in s.alive) return Step.Rejected("Você não vota agora")
                if (target == null || target !in s.candidates || target == by) return Step.Rejected("Escolhe outra pessoa")
                val next = s.copy(votes = s.votes.vote(by, target))
                Step.Ok(if (next.votes.complete) resolveVote(next, now) else next)
            }
            UStage.GUESS -> {
                if (a.type != Act.TEXT || by != s.eliminated) return Step.Rejected("Só o Sr. Branco chuta")
                val text = a.data["value"]?.jsonPrimitive?.content.orEmpty().take(40)
                val right = Text.same(text, s.civilWord)
                val next = s.copy(guess = text)
                Step.Ok(if (right) next.copy(stage = UStage.DONE, winner = Side.BRANCO, deadline = null) else verdict(next, now))
            }
            else -> Step.Rejected("Agora é só olhar pra TV")
        }
    }

    override fun host(s: UndercoverState, cmd: HostCmd, now: Long): UndercoverState = when (s.stage) {
        UStage.WORDS -> clues(s, now)
        UStage.CLUES -> nextSpeaker(s, now)
        UStage.VOTE -> resolveVote(s, now)
        UStage.ELIM -> afterElimination(s, now)
        UStage.GUESS -> verdict(s.copy(guess = ""), now)
        UStage.DONE -> s
    }

    override fun timeout(s: UndercoverState, now: Long) = host(s, HostCmd.CONTINUE, now)

    override fun tvAction(s: UndercoverState) = when (s.stage) {
        UStage.WORDS -> TvAction("Começar as pistas", hold = true)
        UStage.CLUES -> TvAction("Próxima pessoa")
        UStage.VOTE -> TvAction("Encerrar a votação", hold = true)
        UStage.ELIM -> TvAction("Continuar")
        UStage.GUESS -> TvAction("Pular o palpite", hold = true)
        UStage.DONE -> null
    }

    override fun deadline(s: UndercoverState) = s.deadline

    override fun phase(s: UndercoverState) = "${s.stage.name.lowercase()}:${s.round}:${s.speaker}:${s.revote}"

    override fun mood(s: UndercoverState) = when (s.stage) {
        UStage.WORDS, UStage.VOTE -> Mood.SECRET
        UStage.CLUES, UStage.GUESS -> Mood.FOCUS
        else -> Mood.TALK
    }

    override fun phone(s: UndercoverState, p: PlayerId): PhoneView {
        val word = s.wordOf(p)
        val badge = when {
            s.stage == UStage.DONE -> null
            s.roles[p] == Role.BRANCO -> Badge("Você é o Sr. Branco", "sem palavra")
            word != null -> Badge("Sua palavra", word)
            else -> null
        }
        fun wait(text: String, sub: String? = null, react: Boolean = false, buttons: List<Button> = emptyList()) =
            PhoneView(phase(s), badge = badge, body = Widget.Wait(text, sub, react), endsAt = s.deadline, buttons = buttons)
        return when (s.stage) {
            UStage.WORDS -> PhoneView(
                phase(s), "Rodada 1", badge,
                body = Widget.Secret(if (s.roles[p] == Role.BRANCO) "Você é o Sr. Branco" else "Sua palavra", word ?: "sem palavra"),
                endsAt = s.deadline,
                buttons = if (p in s.ready) emptyList() else listOf(Button("ready", "Já vi")),
            )
            UStage.CLUES -> when {
                p !in s.alive -> wait("Olha pra TV", "Você já saiu", react = true)
                p == s.speakerId -> PhoneView(phase(s), "Sua vez", badge, Widget.Wait("Fala uma pista em voz alta", "Sem falar a palavra"), s.deadline, listOf(Button("done", "Terminei")))
                else -> wait("Escuta ${s.name(s.speakerId ?: "")}", react = false)
            }
            UStage.VOTE -> if (p in s.alive) PhoneView(
                phase(s), if (s.revote) "Desempate" else "Quem é o infiltrado?", badge,
                Widget.PickPlayer("Quem sai?", s.candidates.filter { it != p }, chosen = s.votes.cast[p]), s.deadline,
            ) else wait("Olha pra TV", "Você já saiu")
            UStage.ELIM -> wait("Olha pra TV", react = true)
            UStage.GUESS -> if (p == s.eliminated) PhoneView(phase(s), "Última chance", badge, Widget.TextInput("Qual era a palavra dos civis?", "uma palavra"), s.deadline)
            else wait("O Sr. Branco está chutando…")
            UStage.DONE -> wait("Acabou a partida", "Olha pra TV", react = true).copy(badge = null)
        }
    }

    override fun left(s: UndercoverState, p: PlayerId, now: Long): UndercoverState {
        val rest = s.copy(
            players = s.players.filterNot { it.id == p }, alive = s.alive - p, ready = s.ready - p,
            votes = s.votes.without(p), candidates = s.candidates - p,
            speaker = if (s.alive.indexOf(p).let { it in 0 until s.speaker }) s.speaker - 1 else s.speaker,
        )
        if (rest.stage == UStage.DONE) return rest
        val civils = rest.alive.count { rest.roles[it] == Role.CIVIL }
        val bad = rest.alive.size - civils
        return when {
            bad == 0 -> rest.copy(stage = UStage.DONE, winner = Side.CIVIS, deadline = null)
            bad >= civils -> rest.copy(stage = UStage.DONE, winner = Side.INFILTRADOS, deadline = null)
            rest.stage == UStage.WORDS && rest.ready.containsAll(rest.alive) -> clues(rest, now)
            rest.stage == UStage.CLUES && rest.speaker >= rest.alive.size -> startVote(rest, rest.alive, false, now)
            rest.stage == UStage.VOTE && rest.votes.complete -> resolveVote(rest, now)
            else -> rest
        }
    }

    override fun over(s: UndercoverState): Outcome? {
        val winner = s.winner ?: return null
        val winners = s.roles.filterValues { r ->
            when (winner) { Side.CIVIS -> r == Role.CIVIL; Side.INFILTRADOS -> r == Role.INFILTRADO; Side.BRANCO -> r == Role.BRANCO }
        }.keys
        val chips = winners.associateWith { if (winner == Side.BRANCO) 5 else 3 }
        val headline = when (winner) {
            Side.CIVIS -> "Os civis pegaram todos"
            Side.INFILTRADOS -> "Os infiltrados passaram despercebidos"
            Side.BRANCO -> "O Sr. Branco adivinhou: “${s.civilWord}”"
        }
        return Outcome(chips, headline, winners.map { Highlight("INFILTRATED_WIN".takeIf { winner != Side.CIVIS } ?: "CAUGHT_THEM", it) })
    }

    override fun secrets(s: UndercoverState, p: PlayerId): Set<String> {
        if (s.stage == UStage.DONE) return emptySet()
        return setOf(s.civilWord, s.infiltratorWord).filter { it != s.wordOf(p) }.toSet()
    }
}

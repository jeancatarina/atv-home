package dev.jean.atvhome.social.games.spyfall

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class SStage { ROLES, TALK, PICK, VOTE_ACCUSATION, GUESS, FINAL, DONE }
enum class SWinner { CIVIS, ESPIAO }

data class SpyState(
    val players: List<Player>,
    /** Every place the spy could be thinking of: public, shown on the TV. */
    val places: List<String>,
    val location: String,
    val roles: Map<PlayerId, String>,
    val spy: PlayerId,
    val stage: SStage = SStage.ROLES,
    val ready: Set<PlayerId> = emptySet(),
    val deadline: Long? = null,
    /** Time left on the talk clock while an accusation or a guess pauses it. */
    val remaining: Long = 0,
    val accuser: PlayerId? = null,
    val accused: PlayerId? = null,
    val used: Set<PlayerId> = emptySet(),
    val yes: Votes<Int> = Votes(emptySet()),
    val finalVotes: Votes<PlayerId> = Votes(emptySet()),
    val winner: SWinner? = null,
    val why: String = "",
    val talkMs: Long = 420_000,
) {
    fun name(id: PlayerId?) = players.firstOrNull { it.id == id }?.name ?: "?"
}

/**
 * Lugar Secreto: everybody but the spy knows the place and has a role there. People ask each other
 * questions in turn; the spy tries to work out the place, the others try to unmask the spy.
 */
object Spyfall : Game<SpyState> {
    private const val ROLES_MS = 60_000L
    private const val PICK_MS = 15_000L
    private const val ACCUSE_MS = 20_000L
    private const val GUESS_MS = 25_000L
    private const val FINAL_MS = 30_000L
    private val TIMES = listOf(300_000L, 420_000L, 600_000L)

    override val info = GameInfo(
        id = "spyfall", name = "Lugar Secreto", tagline = "Um de vocês não sabe onde está",
        category = Category.DEDUCTION, minPlayers = 4, maxPlayers = 8, idealMin = 5, idealMax = 7, minutes = 8, difficulty = 2,
        style = "Segura pra ver, acusa quando desconfiar",
        howTo = listOf(
            "Todo mundo vê o local e o seu papel, menos o espião. Os locais possíveis aparecem na TV",
            "Façam perguntas uns pros outros, em voz alta, sem entregar o local",
            "Qualquer um pode acusar, mas todo mundo tem que concordar. O espião pode chutar o local a qualquer hora",
        ),
        settings = listOf(Setting("time", "Tempo", listOf("5 min", "7 min", "10 min"), default = 1)),
    )

    override fun setup(ctx: Setup): SpyState {
        val rows = ctx.content.map { it.split("|") }.filter { it.size >= 3 }.ifEmpty { listOf(listOf("Padaria", "Padeiro", "Cliente")) }
        val row = rows.random(ctx.random)
        val spy = ctx.players.random(ctx.random).id
        val roleNames = row.drop(1).shuffled(ctx.random)
        val roles = ctx.players.filter { it.id != spy }.mapIndexed { i, p -> p.id to roleNames[i % roleNames.size] }.toMap()
        return SpyState(
            ctx.players, rows.map { it[0] }, row[0], roles, spy, deadline = ctx.now + ROLES_MS,
            talkMs = TIMES[(ctx.settings["time"] ?: 1).coerceIn(0, TIMES.lastIndex)],
        )
    }

    private fun talk(s: SpyState, now: Long, left: Long = s.talkMs) = s.copy(stage = SStage.TALK, deadline = now + left, accuser = null, accused = null)

    private fun win(s: SpyState, who: SWinner, why: String) = s.copy(stage = SStage.DONE, winner = who, why = why, deadline = null)

    override fun act(s: SpyState, by: PlayerId, a: Action, now: Long): Step<SpyState> {
        val key = a.data["key"]?.jsonPrimitive?.content
        return when (s.stage) {
            SStage.ROLES -> {
                if (a.type != Act.BUTTON || key != "ready") return Step.Rejected("Toca em “Já vi”")
                val n = s.copy(ready = s.ready + by)
                Step.Ok(if (n.ready.containsAll(s.players.map { it.id })) talk(n, now) else n)
            }
            SStage.TALK -> when {
                a.type == Act.BUTTON && key == "accuse" ->
                    if (by in s.used) Step.Rejected("Você já acusou nessa rodada")
                    else Step.Ok(s.copy(stage = SStage.PICK, accuser = by, remaining = (s.deadline ?: now) - now, deadline = now + PICK_MS))
                a.type == Act.BUTTON && key == "guess" ->
                    if (by != s.spy) Step.Rejected("Só o espião chuta o local")
                    else Step.Ok(s.copy(stage = SStage.GUESS, remaining = (s.deadline ?: now) - now, deadline = now + GUESS_MS))
                else -> Step.Rejected("Continuem conversando")
            }
            SStage.PICK -> {
                val target = a.data["id"]?.jsonPrimitive?.content
                if (a.type != Act.PICK || by != s.accuser) return Step.Rejected("Agora é ${s.name(s.accuser)} que acusa")
                if (target == null || target == by || s.players.none { it.id == target }) return Step.Rejected("Escolhe outra pessoa")
                Step.Ok(s.copy(
                    stage = SStage.VOTE_ACCUSATION, accused = target, used = s.used + by, deadline = now + ACCUSE_MS,
                    yes = Votes(s.players.map { it.id }.toSet() - target - by),
                ))
            }
            SStage.VOTE_ACCUSATION -> {
                val v = a.data["key"]?.jsonPrimitive?.content?.toIntOrNull()
                if (a.type != Act.CHOOSE || by !in s.yes.voters || v !in 0..1) return Step.Rejected("Você não vota nessa acusação")
                val n = s.copy(yes = s.yes.vote(by, v!!))
                Step.Ok(if (n.yes.complete) judge(n, now) else n)
            }
            SStage.GUESS -> {
                val idx = a.data["key"]?.jsonPrimitive?.content?.toIntOrNull()
                if (a.type != Act.CHOOSE || by != s.spy || idx == null || idx !in s.places.indices) return Step.Rejected("Escolhe um local")
                Step.Ok(if (s.places[idx] == s.location) win(s, SWinner.ESPIAO, "O espião acertou: ${s.location}") else win(s, SWinner.CIVIS, "O espião errou o local"))
            }
            SStage.FINAL -> {
                val target = a.data["id"]?.jsonPrimitive?.content
                if (a.type != Act.PICK || by !in s.finalVotes.voters || target == null || target == by || s.players.none { it.id == target }) return Step.Rejected("Escolhe outra pessoa")
                val n = s.copy(finalVotes = s.finalVotes.vote(by, target))
                Step.Ok(if (n.finalVotes.complete) endFinal(n) else n)
            }
            SStage.DONE -> Step.Rejected("Olha pra TV")
        }
    }

    private fun judge(s: SpyState, now: Long): SpyState {
        val unanimous = s.yes.voters.isNotEmpty() && s.yes.cast.size == s.yes.voters.size && s.yes.cast.values.all { it == 1 }
        return when {
            !unanimous -> talk(s, now, s.remaining)
            s.accused == s.spy -> win(s, SWinner.CIVIS, "${s.name(s.accused)} era o espião")
            else -> win(s, SWinner.ESPIAO, "${s.name(s.accused)} não era o espião")
        }
    }

    private fun endFinal(s: SpyState): SpyState {
        val leaders = s.finalVotes.leaders()
        return if (leaders.size == 1 && leaders.single() == s.spy) win(s, SWinner.CIVIS, "A sala achou o espião") else win(s, SWinner.ESPIAO, "O espião escapou")
    }

    override fun host(s: SpyState, cmd: HostCmd, now: Long): SpyState = when (s.stage) {
        SStage.ROLES -> talk(s, now)
        SStage.TALK -> s.copy(stage = SStage.FINAL, finalVotes = Votes(s.players.map { it.id }.toSet()), deadline = now + FINAL_MS)
        SStage.PICK -> talk(s, now, s.remaining)
        SStage.VOTE_ACCUSATION -> judge(s, now)
        SStage.GUESS -> win(s, SWinner.CIVIS, "O espião não arriscou")
        SStage.FINAL -> endFinal(s)
        SStage.DONE -> s
    }

    override fun timeout(s: SpyState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: SpyState) = when (s.stage) {
        SStage.ROLES -> TvAction("Começar", hold = true)
        SStage.TALK -> TvAction("Ir para a votação final", hold = true)
        SStage.DONE -> null
        else -> TvAction("Avançar", hold = true)
    }

    override fun deadline(s: SpyState) = s.deadline
    override fun phase(s: SpyState) = "${s.stage.name.lowercase()}:${s.used.size}"
    override fun mood(s: SpyState) = when (s.stage) { SStage.TALK -> Mood.TALK; SStage.DONE -> Mood.TALK; else -> Mood.FOCUS }

    override fun phone(s: SpyState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val spy = p == s.spy
        val badge = if (spy) Badge("Você é o espião", "descubra o local") else Badge("Local · seu papel", "${s.location} · ${s.roles[p]}")
        fun w(t: String, sub: String? = null, buttons: List<Button> = emptyList()) = PhoneView(ph, badge = badge, body = Widget.Wait(t, sub), endsAt = s.deadline, buttons = buttons)
        return when (s.stage) {
            SStage.ROLES -> PhoneView(
                ph, "Seu papel", badge,
                Widget.Secret(if (spy) "Você é o espião" else "Local · seu papel", if (spy) "Descubra o local" else "${s.location} · ${s.roles[p]}"), s.deadline,
                if (p in s.ready) emptyList() else listOf(Button("ready", "Já vi")),
            )
            SStage.TALK -> w(
                "Conversem", "Pergunta alguma coisa pra alguém, em voz alta",
                (if (p in s.used) emptyList() else listOf(Button("accuse", "Acusar"))) + if (spy) listOf(Button("guess", "Sei o local")) else emptyList(),
            )
            SStage.PICK -> if (p == s.accuser) PhoneView(ph, "Quem é o espião?", badge, Widget.PickPlayer("Acuse alguém", s.players.map { it.id }.filter { it != p }), s.deadline)
            else w("${s.name(s.accuser)} vai acusar alguém…")
            SStage.VOTE_ACCUSATION -> if (p in s.yes.voters) PhoneView(
                ph, "Acusação", badge, Widget.Choose("${s.name(s.accuser)} acusa ${s.name(s.accused)}. Concorda?", listOf(Option("1", "Sim, é o espião"), Option("0", "Não")), s.yes.cast[p]?.toString()), s.deadline,
            ) else w(if (p == s.accused) "Te acusaram! Se defende." else "Esperando os votos")
            SStage.GUESS -> if (spy) PhoneView(ph, "Onde estamos?", badge, Widget.Choose("Escolhe o local", s.places.mapIndexed { i, n -> Option("$i", n) }), s.deadline)
            else w("O espião vai arriscar o local…")
            SStage.FINAL -> if (p in s.finalVotes.voters) PhoneView(ph, "Votação final", badge, Widget.PickPlayer("Quem é o espião?", s.players.map { it.id }.filter { it != p }, s.finalVotes.cast[p]), s.deadline)
            else w("Olha pra TV")
            SStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: SpyState, p: PlayerId, now: Long): SpyState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, ready = s.ready - p, yes = s.yes.without(p), finalVotes = s.finalVotes.without(p))
        return when {
            s.stage == SStage.DONE -> rest
            p == s.spy -> win(rest, SWinner.CIVIS, "O espião saiu do jogo")
            rest.players.size < 3 -> win(rest, SWinner.ESPIAO, "Faltou gente pra continuar")
            p == s.accuser || p == s.accused -> if (rest.stage == SStage.PICK || rest.stage == SStage.VOTE_ACCUSATION) talk(rest, now, rest.remaining) else rest
            rest.stage == SStage.ROLES && rest.ready.containsAll(rest.players.map { it.id }) -> talk(rest, now)
            rest.stage == SStage.VOTE_ACCUSATION && rest.yes.complete -> judge(rest, now)
            rest.stage == SStage.FINAL && rest.finalVotes.complete -> endFinal(rest)
            else -> rest
        }
    }

    override fun over(s: SpyState): Outcome? {
        val w = s.winner ?: return null
        val chips = s.players.filter { (it.id == s.spy) == (w == SWinner.ESPIAO) }.associate { it.id to 3 }
        return Outcome(chips, s.why, listOf(Highlight(if (w == SWinner.CIVIS) "SPY_CAUGHT" else "SPY_ESCAPED", s.spy)))
    }

    override fun secrets(s: SpyState, p: PlayerId): Set<String> = when {
        s.stage == SStage.DONE -> emptySet()
        p == s.spy -> if (s.stage == SStage.GUESS) emptySet() else setOf(s.location)
        else -> emptySet()
    }
}

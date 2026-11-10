package dev.jean.atvhome.social.games.psychologist

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive

enum class PStage { READ, ASK, JUDGE, DONE }

data class PsychologistState(
    val players: List<Player>,
    val rule: String,
    val examples: List<String>,
    val psychologist: PlayerId,
    val stage: PStage = PStage.READ,
    val ready: Set<PlayerId> = emptySet(),
    val lives: Int = 3,
    val guesses: Int = 0,
    val judge: Votes<Int> = Votes(emptySet()),
    val remaining: Long = 0,
    val deadline: Long? = null,
    val won: Boolean? = null,
    val askMs: Long = 480_000,
) {
    val others: List<PlayerId> get() = players.map { it.id } - psychologist
}

/**
 * Psicólogo: everyone but the psychologist follows a secret rule when answering. The psychologist
 * asks anything of anyone and has three guesses at the rule; the room decides if each one is right.
 */
object Psychologist : Game<PsychologistState> {
    private const val READ_MS = 60_000L
    private const val JUDGE_MS = 15_000L

    override val info = GameInfo(
        id = "psychologist", name = "Psicólogo", tagline = "Todos seguem uma regra. Só você não sabe qual",
        category = Category.TALK, minPlayers = 4, maxPlayers = 12, idealMin = 5, idealMax = 10, minutes = 8, difficulty = 2,
        style = "Segura pra ver a regra, um toque por palpite",
        howTo = listOf(
            "Um de vocês é o psicólogo e fica de costas. O resto lê a regra secreta no celular",
            "O psicólogo pergunta o que quiser e todo mundo responde seguindo a regra, sem entregar qual é",
            "O psicólogo tem 3 palpites. A sala julga cada um: acertou, ganhou",
        ),
    )

    override fun setup(ctx: Setup): PsychologistState {
        val row = ctx.content.map { it.split("|") }.filter { it.size >= 2 }.ifEmpty { listOf(listOf("Responda sempre com uma pergunta", "— Que horas são? — E você tem pressa?")) }.random(ctx.random)
        return PsychologistState(ctx.players, row[0], row.drop(1), ctx.players.random(ctx.random).id, deadline = ctx.now + READ_MS)
    }

    private fun ask(s: PsychologistState, now: Long, left: Long = s.askMs) = s.copy(stage = PStage.ASK, deadline = now + left)
    private fun finish(s: PsychologistState, won: Boolean) = s.copy(stage = PStage.DONE, won = won, deadline = null)

    private fun verdict(s: PsychologistState, now: Long): PsychologistState {
        val yes = s.judge.cast.values.count { it == 1 }
        return when {
            yes * 2 > s.judge.voters.size -> finish(s, true)
            s.lives - 1 <= 0 -> finish(s.copy(lives = 0), false)
            else -> ask(s.copy(lives = s.lives - 1), now, s.remaining)
        }
    }

    override fun act(s: PsychologistState, by: PlayerId, a: Action, now: Long): Step<PsychologistState> {
        val key = a.data["key"]?.jsonPrimitive?.content
        return when (s.stage) {
            PStage.READ -> {
                if (a.type != Act.BUTTON || key != "ready" || by == s.psychologist) return Step.Rejected("Aguarde")
                val n = s.copy(ready = s.ready + by)
                Step.Ok(if (n.ready.containsAll(s.others)) ask(n, now) else n)
            }
            PStage.ASK -> {
                if (a.type != Act.BUTTON || key != "guess" || by != s.psychologist) return Step.Rejected("Só o psicólogo chuta")
                Step.Ok(s.copy(stage = PStage.JUDGE, guesses = s.guesses + 1, judge = Votes(s.others.toSet()), remaining = (s.deadline ?: now) - now, deadline = now + JUDGE_MS))
            }
            PStage.JUDGE -> {
                val v = key?.toIntOrNull()
                if (a.type != Act.CHOOSE || by !in s.judge.voters || v !in 0..1) return Step.Rejected("Você não julga esse palpite")
                val n = s.copy(judge = s.judge.vote(by, v!!))
                Step.Ok(if (n.judge.complete) verdict(n, now) else n)
            }
            PStage.DONE -> Step.Rejected("Olha pra TV")
        }
    }

    override fun host(s: PsychologistState, cmd: HostCmd, now: Long) = when (s.stage) {
        PStage.READ -> ask(s, now)
        PStage.ASK -> finish(s, false)
        PStage.JUDGE -> verdict(s, now)
        PStage.DONE -> s
    }

    override fun timeout(s: PsychologistState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: PsychologistState) = when (s.stage) {
        PStage.READ -> TvAction("Começar", hold = true)
        PStage.ASK -> TvAction("Encerrar a rodada", hold = true)
        PStage.JUDGE -> TvAction("Encerrar o julgamento", hold = true)
        PStage.DONE -> null
    }

    override fun deadline(s: PsychologistState) = s.deadline
    override fun phase(s: PsychologistState) = "${s.stage.name.lowercase()}:${s.guesses}"
    override fun mood(s: PsychologistState) = if (s.stage == PStage.ASK) Mood.TALK else Mood.FOCUS

    override fun phone(s: PsychologistState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val psy = p == s.psychologist
        return when (s.stage) {
            PStage.READ -> if (psy) PhoneView(ph, body = Widget.Wait("Você é o psicólogo", "Vira de costas enquanto o resto lê a regra"), endsAt = s.deadline)
            else PhoneView(
                ph, "Regra secreta", Badge("Exemplos", s.examples.joinToString("  ·  ")), Widget.Secret("Regra secreta", s.rule), s.deadline,
                if (p in s.ready) emptyList() else listOf(Button("ready", "Já li")),
            )
            PStage.ASK -> if (psy) PhoneView(ph, "Vidas: ${"♥".repeat(s.lives)}", body = Widget.Wait("Faz perguntas e descobre a regra", "Palpites usados: ${s.guesses}"), endsAt = s.deadline, buttons = listOf(Button("guess", "Vou chutar")))
            else PhoneView(ph, "Siga a regra", Badge("Regra", s.rule), Widget.Wait("Responde seguindo a regra", "Sem entregar!", react = true), s.deadline)
            PStage.JUDGE -> if (psy) PhoneView(ph, body = Widget.Wait("A sala está julgando o seu palpite"), endsAt = s.deadline)
            else PhoneView(ph, "Palpite do psicólogo", Badge("Regra", s.rule), Widget.Choose("Acertou a regra?", listOf(Option("1", "Sim"), Option("0", "Não")), s.judge.cast[p]?.toString()), s.deadline)
            PStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: PsychologistState, p: PlayerId, now: Long): PsychologistState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, ready = s.ready - p, judge = s.judge.without(p))
        return when {
            s.stage == PStage.DONE -> rest
            p == s.psychologist || rest.players.size < 4 -> finish(rest, false)
            rest.stage == PStage.READ && rest.ready.containsAll(rest.others) -> ask(rest, now)
            rest.stage == PStage.JUDGE && rest.judge.complete -> verdict(rest, now)
            else -> rest
        }
    }

    override fun over(s: PsychologistState): Outcome? {
        val won = s.won ?: return null
        val name = s.players.firstOrNull { it.id == s.psychologist }?.name ?: "O psicólogo"
        return if (won) Outcome(mapOf(s.psychologist to 3) + s.others.associateWith { 1 }, "$name descobriu: “${s.rule}”", listOf(Highlight("DIAGNOSED", s.psychologist, s.guesses)))
        else Outcome(s.others.associateWith { 2 }, "Não rolou. A regra era: “${s.rule}”")
    }

    override fun secrets(s: PsychologistState, p: PlayerId): Set<String> =
        if (p == s.psychologist && s.stage != PStage.DONE) setOf(s.rule) + s.examples else emptySet()
}

package dev.jean.atvhome.social.games.tribunal

import dev.jean.atvhome.social.game.*
import dev.jean.atvhome.social.kit.Votes
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

enum class TrStage { INTRO, PROOF, VERDICT, DONE }

/** A suspect's three attributes: where, with what, and when. */
data class Alibi(val place: String, val thing: String, val time: String)

data class TribunalState(
    val players: List<Player>,
    val crime: String,
    val alibis: Map<PlayerId, Alibi>,
    val culprit: PlayerId,
    /** The order the culprit's attributes are revealed in: 0 place, 1 thing, 2 time. */
    val proofOrder: List<Int>,
    /** Two things each person saw about somebody else, true. */
    val facts: Map<PlayerId, List<String>>,
    val stage: TrStage = TrStage.INTRO,
    val proof: Int = 0,
    val ready: Set<PlayerId> = emptySet(),
    val objections: Map<PlayerId, Int> = emptyMap(),
    val lastObjection: PlayerId? = null,
    val votes: Votes<PlayerId> = Votes(emptySet()),
    val deadline: Long? = null,
) {
    fun name(id: PlayerId?) = players.firstOrNull { it.id == id }?.name ?: "?"
    fun attr(a: Alibi, which: Int) = when (which) { 0 -> a.place; 1 -> a.thing; else -> a.time }
    /** The public proofs shown so far. */
    fun proofs(): List<String> = proofOrder.take(if (stage == TrStage.INTRO) 0 else proof + 1).map { w ->
        val v = attr(alibis.getValue(culprit), w)
        when (w) { 0 -> "O crime aconteceu em: $v"; 1 -> "Foi usado ou levado: $v"; else -> "Foi por volta de: $v" }
    }
    /** Everyone who still fits all the proofs so far. */
    fun suspects(): List<PlayerId> = players.map { it.id }.filter { id -> proofOrder.take(if (stage == TrStage.INTRO) 0 else proof + 1).all { w -> attr(alibis.getValue(id), w) == attr(alibis.getValue(culprit), w) } }
}

/**
 * Tribunal dos Amigos: an absurd crime, one culprit among the players. Each person has an alibi (a
 * place, an object and a time) and two true things they saw about others. Three public proofs
 * describe the culprit; innocents tell the truth, the culprit lies. After the third proof exactly
 * one person fits, which makes every case solvable by construction.
 */
object Tribunal : Game<TribunalState> {
    private const val INTRO_MS = 120_000L
    private const val PROOF_MS = 90_000L
    private const val VERDICT_MS = 45_000L
    private const val OBJECTION_BONUS_MS = 10_000L
    private const val OBJECTIONS = 2

    override val info = GameInfo(
        id = "tribunal", name = "Tribunal dos Amigos", tagline = "Um crime absurdo, um culpado entre vocês",
        category = Category.DEDUCTION, minPlayers = 4, maxPlayers = 8, idealMin = 5, idealMax = 7, minutes = 25, difficulty = 3,
        style = "Lê seu dossiê, interroga, objeta e vota",
        howTo = listOf(
            "Cada um lê o próprio dossiê: o seu álibi e duas coisas que viu. O culpado também sabe que é ele",
            "A TV solta 3 provas sobre o culpado, uma de cada vez. Interroguem uns aos outros em voz alta: inocente fala a verdade, culpado mente",
            "Dá pra gritar “Objeção!” (duas vezes). No fim, votem no culpado",
        ),
    )

    private fun pickValues(list: List<String>, random: Random) = list.shuffled(random)

    override fun setup(ctx: Setup): TribunalState {
        val row = ctx.content.map { it.split("|") }.filter { it.size >= 4 }.ifEmpty {
            listOf(listOf("O sumiço do bolo", "cozinha,sala,quintal,garagem,varanda,quarto,escada,porão", "faca,vassoura,lanterna,chapéu,balde,mala,guarda-chuva,martelo", "18h,19h,20h,21h,22h,23h,meia-noite,1h"))
        }.random(ctx.random)
        val places = row[1].split(","); val things = row[2].split(","); val times = row[3].split(",")
        // Unique alibis: no two people share all three attributes
        val seen = HashSet<Alibi>()
        val alibis = HashMap<PlayerId, Alibi>()
        for (p in ctx.players) {
            var a: Alibi
            var tries = 0
            do { a = Alibi(places.random(ctx.random), things.random(ctx.random), times.random(ctx.random)) } while (!seen.add(a) && ++tries < 500)
            alibis[p.id] = a
        }
        val culprit = ctx.players.random(ctx.random).id
        val facts = ctx.players.associate { p ->
            val others = ctx.players.filter { it.id != p.id }.shuffled(ctx.random).take(2)
            p.id to others.map { o ->
                val a = alibis.getValue(o.id)
                when (ctx.random.nextInt(3)) {
                    0 -> "Você viu ${o.name} em: ${a.place}"
                    1 -> "Você viu ${o.name} com: ${a.thing}"
                    else -> "Você viu ${o.name} por volta de: ${a.time}"
                }
            }
        }
        return TribunalState(ctx.players, row[0], alibis, culprit, listOf(0, 1, 2).shuffled(ctx.random), facts, deadline = ctx.now + INTRO_MS)
    }

    private fun proofStage(s: TribunalState, n: Int, now: Long) = s.copy(stage = TrStage.PROOF, proof = n, deadline = now + PROOF_MS)
    private fun verdict(s: TribunalState, now: Long) = s.copy(stage = TrStage.VERDICT, votes = Votes(s.players.map { it.id }.toSet()), deadline = now + VERDICT_MS)
    private fun tally(s: TribunalState) = s.copy(stage = TrStage.DONE, deadline = null)

    override fun act(s: TribunalState, by: PlayerId, a: Action, now: Long): Step<TribunalState> {
        val key = a.data["key"]?.jsonPrimitive?.content
        return when (s.stage) {
            TrStage.INTRO -> {
                if (a.type != Act.BUTTON || key != "ready") return Step.Rejected("Toca em “Já li”")
                val n = s.copy(ready = s.ready + by)
                Step.Ok(if (n.ready.containsAll(s.players.map { it.id })) proofStage(n, 0, now) else n)
            }
            TrStage.PROOF -> {
                if (a.type != Act.BUTTON || key != "objection") return Step.Rejected("Discutam em voz alta")
                if ((s.objections[by] ?: 0) >= OBJECTIONS) return Step.Rejected("Acabaram suas objeções")
                Step.Ok(s.copy(objections = s.objections + (by to (s.objections[by] ?: 0) + 1), lastObjection = by, deadline = (s.deadline ?: now) + OBJECTION_BONUS_MS))
            }
            TrStage.VERDICT -> {
                val t = a.data["id"]?.jsonPrimitive?.content
                if (a.type != Act.PICK || by !in s.votes.voters || t == null || t == by || s.players.none { it.id == t }) return Step.Rejected("Escolhe outra pessoa")
                val n = s.copy(votes = s.votes.vote(by, t))
                Step.Ok(if (n.votes.complete) tally(n) else n)
            }
            TrStage.DONE -> Step.Rejected("Olha pra TV")
        }
    }

    override fun host(s: TribunalState, cmd: HostCmd, now: Long) = when (s.stage) {
        TrStage.INTRO -> proofStage(s, 0, now)
        TrStage.PROOF -> if (s.proof >= 2) verdict(s, now) else proofStage(s.copy(lastObjection = null), s.proof + 1, now)
        TrStage.VERDICT -> tally(s)
        TrStage.DONE -> s
    }

    override fun timeout(s: TribunalState, now: Long) = host(s, HostCmd.CONTINUE, now)
    override fun tvAction(s: TribunalState) = when (s.stage) {
        TrStage.INTRO -> TvAction("Começar o julgamento", hold = true)
        TrStage.PROOF -> TvAction(if (s.proof >= 2) "Ir para o veredito" else "Próxima prova", hold = true)
        TrStage.VERDICT -> TvAction("Encerrar a votação", hold = true)
        TrStage.DONE -> null
    }

    override fun deadline(s: TribunalState) = s.deadline
    override fun phase(s: TribunalState) = "${s.stage.name.lowercase()}:${s.proof}"
    override fun mood(s: TribunalState) = when (s.stage) { TrStage.PROOF, TrStage.DONE -> Mood.TALK; else -> Mood.FOCUS }

    override fun phone(s: TribunalState, p: PlayerId): PhoneView {
        val ph = phase(s)
        val a = s.alibis.getValue(p)
        val guilty = p == s.culprit
        val dossier = Widget.Manual(listOf(
            ManualPage("Sua ficha", listOf("Local: ${a.place}", "Objeto: ${a.thing}", "Horário: ${a.time}", if (guilty) "VOCÊ É O CULPADO: minta sobre a sua ficha." else "Você é inocente: fala a verdade.")),
            ManualPage("O que você viu", s.facts.getValue(p)),
        ))
        return when (s.stage) {
            TrStage.INTRO -> PhoneView(ph, "Seu dossiê", body = dossier, endsAt = s.deadline, buttons = if (p in s.ready) emptyList() else listOf(Button("ready", "Já li")))
            TrStage.PROOF -> PhoneView(ph, "Prova ${s.proof + 1} de 3", body = dossier, endsAt = s.deadline,
                buttons = if ((s.objections[p] ?: 0) < OBJECTIONS) listOf(Button("objection", "Objeção! (${OBJECTIONS - (s.objections[p] ?: 0)})")) else emptyList())
            TrStage.VERDICT -> PhoneView(ph, "Veredito", body = Widget.PickPlayer("Quem é o culpado?", s.players.map { it.id }.filter { it != p }, s.votes.cast[p]), endsAt = s.deadline)
            TrStage.DONE -> PhoneView(ph, body = Widget.Wait("Acabou a partida", "Olha pra TV"))
        }
    }

    override fun left(s: TribunalState, p: PlayerId, now: Long): TribunalState {
        val rest = s.copy(players = s.players.filterNot { it.id == p }, ready = s.ready - p, votes = s.votes.without(p))
        return when {
            s.stage == TrStage.DONE -> rest
            p == s.culprit || rest.players.size < 4 -> tally(rest)
            rest.stage == TrStage.INTRO && rest.ready.containsAll(rest.players.map { it.id }) -> proofStage(rest, 0, now)
            rest.stage == TrStage.VERDICT && rest.votes.complete -> tally(rest)
            else -> rest
        }
    }

    override fun over(s: TribunalState): Outcome? {
        if (s.stage != TrStage.DONE) return null
        val leaders = s.votes.leaders()
        val caught = leaders.size == 1 && leaders.single() == s.culprit && s.culprit in s.players.map { it.id }
        val name = s.name(s.culprit)
        return if (caught) Outcome(s.players.filter { it.id != s.culprit }.associate { it.id to 3 }, "$name era a pessoa culpada: ${s.crime}", listOf(Highlight("GUILTY_CAUGHT", s.culprit)) + s.votes.cast.filterValues { it == s.culprit }.keys.map { Highlight("VERDICT", it) })
        else Outcome(mapOf(s.culprit to 4), "$name escapou do tribunal: ${s.crime}", listOf(Highlight("GUILTY_FREE", s.culprit)))
    }

    override fun secrets(s: TribunalState, p: PlayerId): Set<String> =
        if (s.stage != TrStage.DONE && p != s.culprit) setOf("VOCÊ É O CULPADO") else emptySet()
}

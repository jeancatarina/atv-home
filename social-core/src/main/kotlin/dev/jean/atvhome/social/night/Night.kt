package dev.jean.atvhome.social.night

import dev.jean.atvhome.social.game.Highlight
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.game.PlayerId
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Something worth remembering from a game of the night. */
@Serializable
data class NightEvent(val gameId: String, val kind: String, val who: PlayerId? = null, val n: Int = 0) {
    constructor(gameId: String, h: Highlight) : this(gameId, h.kind, h.who, h.n)
}

@Serializable
data class Award(val key: String, val title: String, val blurb: String, val who: PlayerId)

/** The trophies of the evening: one per memorable moment, and at least one for everybody. */
object Ceremony {

    private class Def(val title: String, val blurb: (name: String, n: Int) -> String, val priority: Int)

    private val DEFS = mapOf(
        "FOOLED" to Def("Cara de Pau", { _, n -> "enganou $n pessoas de uma vez" }, 9),
        "UNANIMOUS" to Def("Alvo Preferido", { _, _ -> "foi unanimidade: ninguém teve dúvida" }, 8),
        "MOST_VOTED" to Def("Estrela da Noite", { _, n -> "levou $n votos da turma" }, 7),
        "SPY_CAUGHT" to Def("Espião de Mentirinha", { _, _ -> "teve o disfarce descoberto rapidinho" }, 6),
        "SPY_ESCAPED" to Def("Agente 007", { _, _ -> "enganou a sala inteira como espião" }, 9),
        "INSIDER_CAUGHT" to Def("Deu Bandeira", { _, _ -> "soprou as respostas, mas a turma percebeu" }, 6),
        "INSIDER_GOLD" to Def("Infiltração de Ouro", { _, _ -> "guiou a turma inteira e ninguém percebeu" }, 9),
        "DIAGNOSED" to Def("Sherlock", { _, n -> "sacou a regra em $n palpite${if (n == 1) "" else "s"}" }, 8),
        "MISSION_MASTER" to Def("Agente Secreto", { _, n -> "cumpriu $n pontos de missões sem ninguém perceber" }, 9),
        "INFILTRATED_WIN" to Def("Disfarce Perfeito", { _, _ -> "se infiltrou e ninguém desconfiou" }, 8),
        "CAUGHT_THEM" to Def("Faro Fino", { _, _ -> "farejou os infiltrados" }, 6),
        "SOLVED" to Def("Detetive", { _, n -> "matou a charada com $n dica${if (n == 1) "" else "s"}" }, 5),
        "SYNC" to Def("Sintonia Total", { _, n -> "chegou a $n% de sintonia" }, 7),
        "MAFIA_WIN" to Def("Mente Criminosa", { _, _ -> "enganou a cidade inteira" }, 9),
        "TOWN_WIN" to Def("Xerife", { _, _ -> "protegeu a cidade" }, 6),
        "DEFUSED" to Def("Nervos de Aço", { _, n -> "desarmou a bomba com $n segundos de sobra" }, 9),
        "BOOM" to Def("Fogos de Artifício", { _, _ -> "fez a bomba explodir com estilo" }, 4),
        "BULLSEYE" to Def("Na Mosca", { _, _ -> "cravou o alvo" }, 8),
        "CINEPHILE" to Def("Rato de Cinema", { _, n -> "acertou o filme na pista $n" }, 8),
        "VERDICT" to Def("Veredito Certeiro", { _, _ -> "acertou o veredito do tribunal" }, 6),
        "GUILTY_CAUGHT" to Def("Caso Resolvido", { _, _ -> "não escapou do tribunal" }, 6),
        "GUILTY_FREE" to Def("Crime Perfeito", { _, _ -> "escapou do tribunal" }, 9),
    )

    fun build(players: List<Player>, chips: Map<PlayerId, Int>, events: List<NightEvent>, random: Random = Random(0)): List<Award> {
        val names = players.associate { it.id to it.name }
        val awards = LinkedHashMap<Pair<String, PlayerId>, Award>()
        val sorted = events.filter { it.who != null && it.who in names && it.kind in DEFS }
            .sortedWith(compareByDescending<NightEvent> { DEFS.getValue(it.kind).priority }.thenByDescending { it.n }.thenBy { names[it.who] })
        for (e in sorted) {
            val who = e.who!!
            val def = DEFS.getValue(e.kind)
            awards.getOrPut(e.kind to who) { Award(e.kind, def.title, "${names[who]} ${def.blurb(names[who] ?: "", e.n)}", who) }
        }
        val list = awards.values.toMutableList()
        // The night's champion, with the most chips (ties share it)
        chips.filterKeys { it in names }.let { c ->
            val top = c.values.maxOrNull()
            if (top != null && top > 0) c.filterValues { it == top }.keys.sortedBy { names[it] }.forEach {
                list += Award("CHAMPION", "Destaque da Noite", "${names[it]} terminou na frente com $top fichas", it)
            }
        }
        // Everybody gets one: the ones left out are thanked for what they did
        val withAward = list.map { it.who }.toSet()
        players.filter { it.id !in withAward }.sortedBy { chips[it.id] ?: 0 }.forEachIndexed { i, p ->
            val (title, blurb) = if (i == 0 && players.size > 2 && (chips[p.id] ?: 0) <= (chips.values.minOrNull() ?: 0))
                "Espírito Esportivo" to "topou todas as partidas, mesmo sem pontuar" else "Presença de Palco" to "marcou presença em todas as partidas"
            list += Award(if (title == "Presença de Palco") "PRESENCE" else "GOOD_SPORT", title, "${p.name} $blurb", p.id)
        }
        // The big ones last: the ceremony builds up to the champion
        return list.sortedBy { if (it.key == "CHAMPION") 1 else 0 }
    }
}

enum class Tone { FAMILY, TEASING }

/**
 * The host's one-liners. Short, specific to what just happened, never repeated in the same night.
 * Pure: the caller passes the lines already said and the random source.
 */
object Presenter {

    private class Lines(val family: List<String>, val teasing: List<String>)

    private val AFTER = mapOf(
        "FOOLED" to Lines(
            listOf("{name} enganou {n} de uma vez. Quem diria.", "{n} pessoas caíram na conversa de {name}. Fica de olho.", "Ninguém desconfiou de {name}. Foram {n} de uma vez."),
            listOf("{name} enganou {n} de uma vez. Cara de pau, hein.", "{n} pessoas caíram na lábia de {name}. Fiquem espertos.", "{name} mente bem demais. Isso preocupa."),
        ),
        "UNANIMOUS" to Lines(
            listOf("Todo mundo apontou {name}. Sem chance de defesa.", "Unanimidade! {name} é a cara dessa pergunta.", "{name} ganhou por aclamação."),
            listOf("Unânime: {name}. Nem adianta se defender.", "Ninguém nem hesitou: era {name}.", "{name}, a turma te conhece bem demais."),
        ),
        "MOST_VOTED" to Lines(listOf("{name} levou mais votos na noite."), listOf("{name} virou o alvo preferido. Fama ou problema?")),
        "SPY_CAUGHT" to Lines(listOf("A sala sacou o disfarce de {name} rapidinho."), listOf("{name} de espião? Descobriram na hora.")),
        "SPY_ESCAPED" to Lines(listOf("{name} escapou como espião. Respeito."), listOf("{name} enganou todo mundo. Tem espião no sofá.")),
        "INSIDER_GOLD" to Lines(listOf("{name} soprou as respostas e ninguém percebeu."), listOf("{name} tava infiltrando e ninguém desconfiou. Assusta.")),
        "INSIDER_CAUGHT" to Lines(listOf("Deram bandeira: descobriram {name}."), listOf("{name} tentou, mas a sala é esperta.")),
        "DIAGNOSED" to Lines(listOf("{name} sacou a regra em {n} palpite(s)."), listOf("{name} descobriu a regra. Tem gente lendo mente aqui.")),
        "INFILTRATED_WIN" to Lines(listOf("Os infiltrados passaram batido."), listOf("Os infiltrados ganharam. Prestem mais atenção!")),
        "CAUGHT_THEM" to Lines(listOf("Os civis pegaram os infiltrados."), listOf("Os civis sacaram tudo. Os infiltrados nem tiveram chance.")),
        "SYNC" to Lines(listOf("Sintonia de {n}%. Que dupla!"), listOf("Sintonia de {n}%. Dá pra melhorar, hein.")),
        "SOLVED" to Lines(listOf("Charada resolvida com {n} dica(s)."), listOf("Resolveram! O Guardião já tava desistindo.")),
        "MISSION_MASTER" to Lines(listOf("{name} foi quem mais cumpriu missão."), listOf("{name} fez tudo escondido. Alguém viu?")),
    )

    private val GENERIC = Lines(
        listOf("Boa rodada!", "Foi boa essa, hein.", "Bora pra próxima?"),
        listOf("Só isso? Quero mais.", "Tem alguém acordado aí?", "Próxima, que essa foi fácil demais."),
    )

    private val START = Lines(
        listOf("Bora de {game}!", "É hora de {game}. Valendo!", "{game}: prestem atenção nas regras."),
        listOf("{game}. Quem vai pagar mico hoje?", "{game}. Que ganhe o menos pior."),
    )

    /** A random line of [l] for [tone], filled in, skipping those already said; when all were said, any of them. */
    private fun choose(l: Lines, tone: Tone, used: Set<String>, random: Random, name: String?, n: Int, game: String?): String {
        val all = (if (tone == Tone.FAMILY) l.family else l.teasing).map { fill(it, name, n, game) }
        val fresh = all.filter { it !in used }.ifEmpty { all }
        return fresh[random.nextInt(fresh.size)]
    }

    /** Names that end in "?" or "!" ("Quem é Mais Provável?") swallow the line's own "." or ":". */
    private fun fill(t: String, name: String?, n: Int, game: String?) =
        t.replace("{name}", name ?: "alguém").replace("{n}", "$n").replace("{game}", game ?: "um jogo").replace(Regex("([?!])[.:]( \\p{Ll})?")) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }

    /** A line for the most memorable thing in [events], or a generic one. */
    fun afterGame(events: List<NightEvent>, players: List<Player>, tone: Tone, used: Set<String>, random: Random): String {
        val best = events.filter { it.kind in AFTER }.maxByOrNull { it.n.coerceAtLeast(1) * 10 + (AFTER.keys.indexOf(it.kind)) }
        val lines = best?.let { AFTER.getValue(it.kind) } ?: GENERIC
        return choose(lines, tone, used, random, players.firstOrNull { it.id == best?.who }?.name, best?.n ?: 0, null)
    }

    fun gameStarting(gameName: String, tone: Tone, used: Set<String>, random: Random): String {
        return choose(START, tone, used, random, null, 0, gameName)
    }
}

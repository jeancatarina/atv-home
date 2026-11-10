package dev.jean.atvhome.social.progress

import dev.jean.atvhome.social.games.GameCatalog
import dev.jean.atvhome.social.profile.Career

/** 1 bronze, 2 prata, 3 ouro. */
data class BadgeDef(val id: String, val name: String, val hint: String, val tier: Int, val test: (Career) -> Boolean)

/** Every conquest, as data: a name, a hint for the locked ones, and a test over the career's numbers. */
object Badges {

    private fun hl(c: Career, kind: String) = c.counters["hl:$kind"] ?: 0
    private fun best(c: Career, kind: String) = c.maxes["hl:$kind"] ?: 0

    private val general = listOf(
        BadgeDef("first_win", "Primeira vitória", "Ganhe uma partida", 1) { it.wins >= 1 },
        BadgeDef("wins_10", "Pegando o jeito", "Ganhe 10 partidas", 2) { it.wins >= 10 },
        BadgeDef("wins_50", "Imbatível", "Ganhe 50 partidas", 3) { it.wins >= 50 },
        BadgeDef("games_10", "Presença confirmada", "Jogue 10 partidas", 1) { it.games >= 10 },
        BadgeDef("games_50", "De casa", "Jogue 50 partidas", 2) { it.games >= 50 },
        BadgeDef("games_100", "Sem vida social", "Jogue 100 partidas", 3) { it.games >= 100 },
        BadgeDef("nights_5", "Noites em claro", "Jogue em 5 noites", 2) { it.nights >= 5 },
        BadgeDef("nights_20", "Tradição da casa", "Jogue em 20 noites", 3) { it.nights >= 20 },
        BadgeDef("explorer", "Curiosidade", "Jogue 5 jogos diferentes", 1) { it.perGame.size >= 5 },
        BadgeDef("all_games", "Cobriu tudo", "Jogue todos os jogos", 3) { it.perGame.size >= GameCatalog.all.size },
        BadgeDef("level_10", "Alma da Festa", "Chegue ao nível 10", 2) { it.level >= 10 },
        BadgeDef("level_25", "Estrela da Sala", "Chegue ao nível 25", 3) { it.level >= 25 },
        BadgeDef("on_fire", "Em chamas", "Ganhe 3 partidas seguidas na mesma noite", 2) { (it.maxes["streakWins"] ?: 0) >= 3 },
        BadgeDef("weeks_3", "Toda semana", "Jogue em 3 semanas seguidas", 2) { (it.maxes["streakWeeks"] ?: 0) >= 3 },
        BadgeDef("big_table", "Casa cheia", "Jogue 5 partidas com 6 pessoas ou mais", 1) { (it.counters["bigTable"] ?: 0) >= 5 },
        BadgeDef("team", "Time bom", "Vença 5 jogos cooperativos", 2) { (it.counters["coopWin"] ?: 0) >= 5 },
    )

    private val special = listOf(
        BadgeDef("spy_007", "Agente 007", "Escape 3 vezes como espião", 3) { hl(it, "SPY_ESCAPED") >= 3 },
        BadgeDef("insider_gold", "Infiltração de Ouro", "Guie o grupo sem ser notado", 2) { hl(it, "INSIDER_GOLD") >= 1 },
        BadgeDef("disguise", "Disfarce Perfeito", "Vença uma rodada de Infiltrados do lado errado", 1) { hl(it, "INFILTRATED_WIN") >= 1 },
        BadgeDef("mafia", "Mente Criminosa", "Vença uma Máfia de Uma Noite como mafioso", 2) { hl(it, "MAFIA_WIN") >= 1 },
        BadgeDef("sherlock", "Sherlock", "Descubra a regra do Psicólogo", 1) { hl(it, "DIAGNOSED") >= 1 },
        BadgeDef("sync_90", "Sintonia Total", "Chegue a 90% de sintonia", 2) { best(it, "SYNC") >= 90 },
        BadgeDef("unanimous_3", "Alvo Preferido", "Seja o alvo unânime 3 vezes", 2) { hl(it, "UNANIMOUS") >= 3 },
        BadgeDef("fooled_4", "Cara de Pau", "Engane 4 pessoas de uma vez", 3) { best(it, "FOOLED") >= 4 },
        BadgeDef("cinephile", "Cinefilia", "Acerte um filme nas duas primeiras pistas", 2) { hl(it, "CINEPHILE") >= 1 },
        BadgeDef("detective", "Detetive", "Resolva um Mistério Sim ou Não", 1) { hl(it, "SOLVED") >= 1 },
        BadgeDef("defuser", "Desarmador", "Desarme uma bomba", 1) { hl(it, "DEFUSED") >= 1 },
        BadgeDef("nerves", "Nervos de Aço", "Desarme com menos de 10 segundos de sobra", 3) { (it.mins["hl:DEFUSED"] ?: Int.MAX_VALUE) < 10 && hl(it, "DEFUSED") >= 1 },
        BadgeDef("agent", "Agente Secreto", "Seja quem mais cumpriu missões", 2) { hl(it, "MISSION_MASTER") >= 1 },
        BadgeDef("verdict_5", "Veredito Certeiro", "Acerte o veredito 5 vezes", 2) { hl(it, "VERDICT") >= 5 },
        BadgeDef("perfect_crime", "Crime Perfeito", "Escape do tribunal", 3) { hl(it, "GUILTY_FREE") >= 1 },
        BadgeDef("star", "Estrela da Noite", "Seja a pessoa mais votada de uma partida", 1) { hl(it, "MOST_VOTED") >= 1 },
    )

    /** "Fera em Frequência": 5 wins in one game. One for each game, made from the catalogue. */
    private val perGame = GameCatalog.all.map { g ->
        BadgeDef("beast_${g.info.id}", "Fera em ${g.info.name}", "Ganhe 5 vezes em ${g.info.name}", 2) { (it.winsPerGame[g.info.id] ?: 0) >= 5 }
    }

    val all: List<BadgeDef> = general + special + perGame

    fun byId(id: String): BadgeDef? = all.firstOrNull { it.id == id }
}

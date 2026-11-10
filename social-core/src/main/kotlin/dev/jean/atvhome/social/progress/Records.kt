package dev.jean.atvhome.social.progress

/** A record of the house: which highlight of which game, and whether more or less is better. */
class RecordDef(val gameId: String, val key: String, val label: String, val kind: String, val higherBetter: Boolean, val unit: String)

object Records {
    val defs = listOf(
        RecordDef("bomb", "spare", "Bomba desarmada com mais tempo de sobra", "DEFUSED", true, "s"),
        RecordDef("frequency", "sync", "Maior sintonia em Frequência", "SYNC", true, "%"),
        RecordDef("couple", "sync", "Maior sintonia de casal", "SYNC", true, "%"),
        RecordDef("twotruths", "fooled", "Mais pessoas enganadas de uma vez", "FOOLED", true, ""),
        RecordDef("mostlikely", "voted", "Mais votos recebidos em uma noite", "MOST_VOTED", true, ""),
        RecordDef("film5", "early", "Filme acertado na pista mais cedo", "CINEPHILE", false, "ª pista"),
        RecordDef("blackstory", "hints", "Mistério resolvido com menos dicas", "SOLVED", false, "dicas"),
        RecordDef("psychologist", "guesses", "Regra descoberta com menos palpites", "DIAGNOSED", false, "palpites"),
        RecordDef("missions", "points", "Mais pontos de missões", "MISSION_MASTER", true, "pts"),
    )

    fun def(gameId: String, key: String) = defs.firstOrNull { it.gameId == gameId && it.key == key }
}

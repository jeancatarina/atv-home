package dev.jean.atvhome.social.kit

import dev.jean.atvhome.social.game.PlayerId
import kotlin.random.Random

/** Who voted for what. Immutable: every vote returns a new value. */
data class Votes<K>(val voters: Set<PlayerId>, val cast: Map<PlayerId, K> = emptyMap()) {

    /** A vote from someone who may vote replaces their earlier one; anyone else is ignored. */
    fun vote(by: PlayerId, choice: K): Votes<K> = if (by in voters) copy(cast = cast + (by to choice)) else this

    fun without(p: PlayerId) = Votes(voters - p, cast - p)

    val complete: Boolean get() = voters.isNotEmpty() && voters.all { it in cast }

    fun tally(): Map<K, Int> = cast.values.groupingBy { it }.eachCount()

    /** Everything tied for the most votes (empty when nobody voted). */
    fun leaders(): List<K> {
        val t = tally()
        val top = t.values.maxOrNull() ?: return emptyList()
        return t.filterValues { it == top }.keys.toList()
    }

    /** All voters picked the same one. */
    fun unanimous(): K? = cast.values.distinct().singleOrNull()?.takeIf { cast.size == voters.size && voters.size >= 2 }
}

object Scores {
    fun add(scores: Map<PlayerId, Int>, who: PlayerId, n: Int): Map<PlayerId, Int> = scores + (who to (scores[who] ?: 0) + n)

    /** The people with the most points, all of them when tied. */
    fun leaders(scores: Map<PlayerId, Int>): List<PlayerId> {
        val top = scores.values.maxOrNull() ?: return emptyList()
        return scores.filterValues { it == top }.keys.toList()
    }
}

object ContentDeck {
    /** [n] items, unseen ones first, in random order; repeats only when the pack runs out. */
    fun draw(pack: List<String>, n: Int, random: Random, seen: Set<String> = emptySet()): List<String> {
        val fresh = pack.filter { it !in seen }.shuffled(random)
        val rest = pack.filter { it in seen }.shuffled(random)
        return (fresh + rest).take(n)
    }
}

object Text {
    /** Lower case, no accents, no spaces or punctuation: "Pão de Queijo!" == "pao de queijo". */
    fun norm(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

    fun same(a: String, b: String) = norm(a).isNotEmpty() && norm(a) == norm(b)
}

/**
 * Memory of what a house has already played, so a game is different every time: the pack handed to
 * a game holds only what has not come up yet, and what the new game used is written down. When the
 * pack is nearly used up it starts over, so the games never run dry.
 */
object Variety {
    /** A short, stable fingerprint of one pack entry (its whole line: some start with a one-letter kind). */
    fun key(item: String): String = Integer.toHexString(Text.norm(item).hashCode())

    /** The entries of [pack] not in [seen]; null when too few are left to make a good game (start over). */
    fun fresh(pack: List<String>, seen: Set<String>, minimum: Int = 12): List<String>? {
        val left = pack.filter { key(it) !in seen }
        return if (left.size >= minOf(minimum, pack.size)) left else null
    }

    /** The entries of [pack] that show up in [state] (a data class tree prints every string it holds). */
    fun used(pack: List<String>, state: Any): List<String> {
        val text = state.toString()
        return pack.filter { item -> item.split('|').take(2).map { it.trim() }.any { it.length >= 4 && text.contains(it) } }
    }
}

/** What the people at the table have already played, to suggest what is new to them. */
object Suggest {
    private const val DAY = 86_400_000L

    /** How stale [gameId] is for these people: higher is better to play. Never played counts most; played tonight, least. */
    fun appeal(gameId: String, people: Collection<dev.jean.atvhome.social.profile.Career>, playedTonight: Boolean, now: Long): Double {
        if (playedTonight) return -1000.0
        if (people.isEmpty()) return 0.0
        return people.sumOf { c ->
            val plays = c.counters["played:$gameId"] ?: 0
            val days = c.lastByGame[gameId]?.let { ((now - it) / DAY).coerceIn(0, 60).toDouble() } ?: 60.0
            (if (plays == 0) 40.0 else 0.0) - plays * 2.0 + days * 0.5
        } / people.size
    }

    /** How many of [people] have never played [gameId]. */
    fun newcomers(gameId: String, people: Collection<dev.jean.atvhome.social.profile.Career>): Int = people.count { (it.counters["played:$gameId"] ?: 0) == 0 }

    /** [games] from the best suggestion to the worst, with a little chance so ties do not always fall the same way. */
    fun order(games: List<dev.jean.atvhome.social.game.Game<*>>, people: Collection<dev.jean.atvhome.social.profile.Career>, tonight: Set<String>, now: Long, random: Random): List<dev.jean.atvhome.social.game.Game<*>> =
        games.sortedByDescending { appeal(it.info.id, people, it.info.id in tonight, now) + random.nextDouble() * 6 }
}

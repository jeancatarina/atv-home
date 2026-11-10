package dev.jean.atvhome.data

/** The outcome shown on the TV and the phones. */
data class MatchOutcome(val key: String, val unanimous: Boolean, val likes: Int, val people: Int)

/**
 * Who liked what, for any number of people. A title everyone in the room liked is a match. There is
 * no "most voted" consolation prize: a title only one person wanted is not a night's choice, so the
 * deck keeps growing ([add]) until the room agrees on something. "Outro match" moves on to the next
 * one without losing votes.
 */
class MatchRules(deck: List<String>) {

    private val deck = ArrayList(deck)
    private val inDeck = HashSet(deck)
    private val names = LinkedHashMap<String, String>() // id → name
    private val likes = HashMap<String, MutableSet<String>>() // card → ids that liked it
    private val voted = HashMap<String, MutableSet<String>>() // id → cards they voted on
    private val shown = HashSet<String>()
    var votes = 0; private set
    var result: MatchOutcome? = null; private set

    val people: List<String> get() = names.values.toList()
    val size: Int get() = deck.size

    /** More cards for the same room; titles already in the deck are ignored. */
    fun add(keys: List<String>) { keys.forEach { if (inDeck.add(it)) deck += it } }

    /** How many cards the fastest swiper still has ahead, who runs out first (the whole deck when nobody is in). */
    fun remaining(): Int = if (names.isEmpty()) deck.size else names.keys.minOf { deck.size - (voted[it]?.size ?: 0) }

    fun join(name: String): String {
        val id = "p${names.size + 1}"
        names[id] = name.trim().take(20).ifBlank { "Pessoa ${names.size + 1}" }
        return id
    }

    fun vote(id: String, key: String, like: Boolean) {
        if (id !in names || key !in inDeck) return
        if (voted.getOrPut(id) { mutableSetOf() }.add(key)) votes++
        if (like) likes.getOrPut(key) { mutableSetOf() }.add(id) else likes[key]?.remove(id)
        if (result == null) result = decide()
    }

    /** "Outro match": keep every vote and look for the next result. */
    fun next() {
        result?.let { shown += it.key }
        result = decide()
    }

    private fun decide(): MatchOutcome? {
        if (names.size < 2) return null
        val everyone = names.keys
        return deck.firstOrNull { it !in shown && likes[it]?.containsAll(everyone) == true }
            ?.let { MatchOutcome(it, unanimous = true, likes = everyone.size, people = everyone.size) }
    }
}

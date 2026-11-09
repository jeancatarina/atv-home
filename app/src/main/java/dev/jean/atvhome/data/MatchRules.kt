package dev.jean.atvhome.data

/** The outcome shown on the TV and the phones. */
data class MatchOutcome(val key: String, val unanimous: Boolean, val likes: Int, val people: Int)

/**
 * Who liked what, for any number of people. A title everyone in the room liked is a match right
 * away; when everyone has gone through the whole deck without one, the most liked title wins
 * ("O mais votado: 7 de 10"). "Outro match" moves on to the next one without losing votes.
 */
class MatchRules(private val deck: List<String>) {

    private val names = LinkedHashMap<String, String>() // id → name
    private val likes = HashMap<String, MutableSet<String>>() // card → ids that liked it
    private val voted = HashMap<String, MutableSet<String>>() // id → cards they voted on
    private val shown = HashSet<String>()
    var votes = 0; private set
    var result: MatchOutcome? = null; private set

    val people: List<String> get() = names.values.toList()

    fun join(name: String): String {
        val id = "p${names.size + 1}"
        names[id] = name.trim().take(20).ifBlank { "Pessoa ${names.size + 1}" }
        return id
    }

    fun vote(id: String, key: String, like: Boolean) {
        if (id !in names || key !in deck) return
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
        deck.firstOrNull { it !in shown && likes[it]?.containsAll(everyone) == true }?.let {
            return MatchOutcome(it, unanimous = true, likes = everyone.size, people = everyone.size)
        }
        val allDone = everyone.all { voted[it]?.size == deck.size }
        if (!allDone) return null
        val best = deck.filter { it !in shown }.maxByOrNull { likes[it]?.size ?: 0 } ?: return null
        val count = likes[best]?.size ?: 0
        return if (count == 0) null else MatchOutcome(best, unanimous = false, likes = count, people = everyone.size)
    }
}

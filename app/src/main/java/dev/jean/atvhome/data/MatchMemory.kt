package dev.jean.atvhome.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

@Serializable
private class MemoryFile(val passed: Map<String, Long> = emptyMap(), val matched: Map<String, Long> = emptyMap())

/**
 * What this house said no to (and what already became a match), so the Match never throws the same
 * title at the room right after it was refused. A refused title is not gone: it only goes to the end
 * of the line and comes back after a while, because people change their mind. Small JSON file.
 */
class MatchMemory(private val file: File? = null, private val clock: () -> Long = System::currentTimeMillis) {

    private val passed = LinkedHashMap<String, Long>()
    private val matched = LinkedHashMap<String, Long>()

    init {
        val text = file?.let { runCatching { it.readText() }.getOrNull() }
        if (text != null) runCatching { Json.decodeFromString<MemoryFile>(text) }.getOrNull()?.let { passed.putAll(it.passed); matched.putAll(it.matched) }
    }

    @Synchronized fun pass(key: String) { passed.remove(key); passed[key] = clock(); save() }

    @Synchronized fun matched(key: String) { matched.remove(key); matched[key] = clock(); save() }

    /** How long ago [key] was last refused or matched, in days; null when never. */
    @Synchronized fun daysSince(key: String): Double? {
        val at = maxOf(passed[key] ?: Long.MIN_VALUE, matched[key] ?: Long.MIN_VALUE)
        return if (at == Long.MIN_VALUE) null else (clock() - at) / DAY.toDouble()
    }

    /**
     * [keys] in the order to show them: first what this house never refused (shuffled), then what was
     * refused long enough ago (the oldest refusal first), and last what was refused only days ago.
     */
    @Synchronized fun order(keys: Collection<String>, random: Random): List<String> {
        val never = ArrayList<String>(); val later = ArrayList<Pair<String, Double>>(); val recent = ArrayList<Pair<String, Double>>()
        keys.distinct().forEach { k ->
            val d = daysSince(k)
            when {
                d == null -> never += k
                d >= COOLDOWN_DAYS -> later += k to d
                else -> recent += k to d
            }
        }
        return never.shuffled(random) + later.sortedByDescending { it.second + random.nextDouble() * 3 }.map { it.first } + recent.sortedByDescending { it.second }.map { it.first }
    }

    private fun save() {
        while (passed.size > MAX) passed.remove(passed.keys.first())
        while (matched.size > MAX) matched.remove(matched.keys.first())
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(Json.encodeToString(MemoryFile.serializer(), MemoryFile(passed, matched)))
            tmp.renameTo(f)
        }
    }

    companion object {
        const val COOLDOWN_DAYS = 10
        private const val MAX = 800
        private const val DAY = 86_400_000L
    }
}

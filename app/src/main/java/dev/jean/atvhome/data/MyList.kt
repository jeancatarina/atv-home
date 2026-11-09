package dev.jean.atvhome.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ListEntry(
    val title: Title,
    val addedAt: Long,
    /** Services where the title was already streaming when last checked. */
    val knownProviders: List<Int> = emptyList(),
    /** "Agora no Prime", "Novo ep. sexta"… set by the daily check, null when there's no news. */
    val badge: String? = null,
)

/** "Minha Lista": a small JSON file, at most 200 titles (oldest go first). */
class MyList(context: Context) {

    private val file = File(context.filesDir, "my_list.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(read())
    val entries: StateFlow<List<ListEntry>> = state

    private fun read(): List<ListEntry> =
        runCatching { json.decodeFromString<List<ListEntry>>(file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    private fun write(entries: List<ListEntry>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(entries))
        tmp.renameTo(file)
        state.value = entries
    }

    fun contains(title: Title) = state.value.any { it.title.key == title.key }

    fun toggle(title: Title, providersNow: List<Int>) {
        val current = state.value
        if (current.any { it.title.key == title.key }) {
            write(current.filterNot { it.title.key == title.key })
        } else {
            write((listOf(ListEntry(title, System.currentTimeMillis(), providersNow)) + current).take(200))
        }
    }

    fun update(transform: (ListEntry) -> ListEntry) = write(state.value.map(transform))

    /** Clears the badge once the user has opened the title. */
    fun seen(title: Title) {
        if (state.value.any { it.title.key == title.key && it.badge != null }) {
            update { if (it.title.key == title.key) it.copy(badge = null) else it }
        }
    }
}

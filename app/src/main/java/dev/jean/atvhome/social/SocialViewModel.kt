package dev.jean.atvhome.social

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.social.game.SocialJson
import dev.jean.atvhome.ui.HomeViewModel
import dev.jean.atvhome.social.room.ExecutorTimer
import dev.jean.atvhome.social.room.FileSeatStore
import dev.jean.atvhome.social.room.Room
import dev.jean.atvhome.social.room.RoomServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the room and its server, and only while the Social area is in use. It is created the first
 * time the screen opens (nothing in App.onCreate), so the launcher pays nothing until then.
 *
 * The room outlives the screen on purpose: opening a title's details from a game covers the Social
 * screen, and the phones must stay connected. It closes 60 s after the last Social screen is gone,
 * and 15 min after the launcher goes to the background (the game is paused at once).
 */
class SocialViewModel(application: Application) : AndroidViewModel(application) {

    var room by mutableStateOf<Room?>(null); private set
    /** "http://192.168.1.3:8766/", without the room key (the key travels in the link's fragment). */
    var baseUrl by mutableStateOf<String?>(null); private set
    var failed by mutableStateOf(false); private set
    /** The host's voice: null is silent. Lines already said stay here, so none repeats during the night. */
    var tone by mutableStateOf<dev.jean.atvhome.social.night.Tone?>(dev.jean.atvhome.social.night.Tone.FAMILY)
    val said = HashSet<String>()
    /** Sound effects on the TV (the phones are always silent). */
    var sound by mutableStateOf(true)
    /** The host's current line, shown as a ribbon on the TV for a few seconds. */
    var ribbon by mutableStateOf<String?>(null); private set
    private var ribbonJob: Job? = null

    fun say(line: String) {
        ribbon = line
        ribbonJob?.cancel()
        ribbonJob = viewModelScope.launch { delay(5_500); ribbon = null }
    }
    /** Films for "Filme em 5 Pistas", as lines the game reads (see Film.parse). Loaded when its sheet opens. */
    @Volatile private var filmPack: List<String> = emptyList()
    var filmsLoading by mutableStateOf(false); private set

    private var server: RoomServer? = null
    private var timer: ExecutorTimer? = null
    private var closeJob: Job? = null
    private var opening = false

    fun shown() {
        closeJob?.cancel()
        if (room == null && !opening) open()
    }

    fun gone() = closeLater(CLOSE_AFTER_LEAVING_MS)

    /** The launcher lost the screen (another app opened, the TV went to sleep): freeze the game. */
    fun background() {
        room?.pause()
        closeLater(CLOSE_AFTER_BACKGROUND_MS)
    }

    private fun closeLater(ms: Long) {
        if (room == null) return
        closeJob?.cancel()
        closeJob = viewModelScope.launch { delay(ms); close() }
    }

    private fun open() {
        opening = true
        failed = false
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val started = withContext(Dispatchers.IO) {
                runCatching {
                    val t = ExecutorTimer()
                    val r = Room(
                        content = { id -> if (id == "film5") filmPack else loadContent(id) },
                        timer = t,
                        store = FileSeatStore(File(ctx.filesDir, "social/room.json")),
                        // The all-time board lives for good in the launcher's own storage
                        ledger = dev.jean.atvhome.social.profile.Ledger(File(ctx.filesDir, "social/ledger.json")),
                    )
                    val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime.toString()
                    val assets: (String) -> ByteArray? = { path ->
                        runCatching { ctx.assets.open("social/$path").use { it.readBytes() } }.getOrNull()
                            // The page names its files by app version so phones can cache them for good
                            ?.let { if (path == "web/index.html") String(it).replace("__V__", version).toByteArray() else it }
                    }
                    // The first free port from 8766: another app may be holding it
                    val s = (RoomServer.PORT..RoomServer.PORT + 4).firstNotNullOfOrNull { port ->
                        runCatching { RoomServer(r, assets, port).also { it.start(5_000, false) } }.getOrNull()
                    } ?: error("no free port")
                    Triple(t, r, s)
                }.onFailure { Log.w(TAG, "open", it) }.getOrNull()
            }
            opening = false
            if (started == null) { failed = true; return@launch }
            timer = started.first
            server = started.third
            baseUrl = started.third.url
            room = started.second
        }
    }

    private fun close() {
        said.clear()
        server?.stop(); server = null
        room?.close(); room = null
        timer?.close(); timer = null
        baseUrl = null
    }

    private fun loadContent(id: String): List<String> = runCatching {
        getApplication<Application>().assets.open("social/content/$id.json").bufferedReader().use { SocialJson.decodeFromString<List<String>>(it.readText()) }
    }.onFailure { Log.w(TAG, "content $id", it) }.getOrDefault(emptyList())

    /** Asks the catalogue for well-rated films on the household's services, once per open room. */
    suspend fun loadFilms(home: HomeViewModel) {
        // Every game of films fetches a new random handful and adds it to the ones kept: with what the
        // house has already played filtered out, the films keep being different
        if (filmsLoading) return
        filmsLoading = true
        val fresh = runCatching {
            home.matchDeck(MediaType.MOVIE).map { item ->
                val d = item.details
                fun clean(t: String?) = t.orEmpty().replace('|', '/').replace('\n', ' ').trim()
                listOf(
                    item.title.key, clean(item.title.name), item.title.year?.toString().orEmpty(), clean(d.genres.take(2).joinToString(", ")),
                    d.runtime?.toString().orEmpty(), clean(d.tagline), clean(d.cast.take(3).joinToString(",") { it.name }),
                    clean(d.cast.firstOrNull()?.character), clean(item.title.overview), TmdbClient.image(item.title.poster, "w342").orEmpty(),
                    clean(home.myProvidersOf(item).firstOrNull()?.let { p -> home.packageFor(p)?.let(home::appFor)?.label ?: p.name }),
                ).joinToString("|")
            }
        }.onFailure { Log.w(TAG, "films", it) }.getOrDefault(emptyList())
        filmPack = (fresh + filmPack).distinctBy { it.substringBefore('|') }.take(120)
        filmsLoading = false
    }

    override fun onCleared() = close()

    companion object {
        private const val TAG = "Social"
        private const val CLOSE_AFTER_LEAVING_MS = 60_000L
        private const val CLOSE_AFTER_BACKGROUND_MS = 15 * 60_000L
    }
}

package dev.jean.atvhome.casa

import android.content.Context
import android.view.KeyEvent
import java.io.File

/**
 * The remote key that opens the house over a film (yellow until the person picks another in "Ver a
 * casa"). A file both processes read: the launcher writes it, and [HouseKeyService] checks its date
 * on each key (one stat, no read) so a new choice applies at once, without restarting anything.
 *
 * A remote with no spare key (only arrows, OK, Back, Home, sound and play) can use a held key
 * instead ([HOLDS]): holding Back or Play/Pause opens the house, a short press still does what it
 * always did ([Hold]).
 */
object Shortcut {
    /** No key: the overlay stays closed and every key goes to the apps. */
    const val NONE = 0
    const val DEFAULT = KeyEvent.KEYCODE_PROG_YELLOW
    val COLORS = listOf(KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_PROG_BLUE)

    /** A held key: stored as [HOLD] + its key code. */
    const val HOLD = 10_000
    const val HOLD_BACK = HOLD + KeyEvent.KEYCODE_BACK
    const val HOLD_PLAY = HOLD + KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
    /**
     * Only keys whose short press can be given back to the app: Back through the accessibility
     * service's global action, Play/Pause through the media session ([HouseKeyService.replay]).
     */
    val HOLDS = listOf(HOLD_BACK, HOLD_PLAY)
    /** How long a key is held to open the house: longer than any normal press. */
    const val HOLD_MS = 600L

    fun isHold(choice: Int) = choice >= HOLD
    /** The key code a choice listens to. */
    fun keyOf(choice: Int) = if (isHold(choice)) choice - HOLD else choice
    fun valid(choice: Int) = choice == NONE || choice in HOLDS || (!isHold(choice) && allowed(choice))

    /** While "Ver a casa" waits for a key, Home lets it through instead of acting on it. */
    @Volatile var capturing = false

    /**
     * Keys that would take something the TV needs away from every app: moving, choosing, going back
     * and home, the Central, sound, power, and playback.
     */
    private val BLOCKED = setOf(
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SETTINGS,
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    )

    fun allowed(code: Int): Boolean = code !in BLOCKED

    fun name(code: Int): String = when (code) {
        NONE -> "Nenhuma"
        HOLD_BACK -> "Segurar Voltar"
        HOLD_PLAY -> "Segurar Play/Pause"
        KeyEvent.KEYCODE_PROG_RED -> "Vermelho"
        KeyEvent.KEYCODE_PROG_GREEN -> "Verde"
        KeyEvent.KEYCODE_PROG_YELLOW -> "Amarelo"
        KeyEvent.KEYCODE_PROG_BLUE -> "Azul"
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "Número ${code - KeyEvent.KEYCODE_0}"
        KeyEvent.KEYCODE_INFO -> "Info"
        KeyEvent.KEYCODE_GUIDE -> "Guia"
        KeyEvent.KEYCODE_CAPTIONS -> "Legendas"
        KeyEvent.KEYCODE_CHANNEL_UP -> "Canal +"
        KeyEvent.KEYCODE_CHANNEL_DOWN -> "Canal −"
        KeyEvent.KEYCODE_TV_INPUT -> "Entrada"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun file(context: Context) = File(context.filesDir, "casa-key.txt")
    @Volatile private var cached = DEFAULT
    @Volatile private var stamp = Long.MIN_VALUE

    fun get(context: Context): Int {
        val f = file(context)
        val modified = f.lastModified() // 0 when there is no file: the default
        if (modified != stamp) {
            cached = if (modified == 0L) DEFAULT else f.readText().trim().toIntOrNull()?.takeIf(::valid) ?: DEFAULT
            stamp = modified
        }
        return cached
    }

    fun set(context: Context, code: Int) {
        require(valid(code)) { "key $code cannot be the shortcut" }
        val f = file(context)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(code.toString())
        tmp.renameTo(f)
    }
}

/**
 * A held shortcut, from the events the remote sends: it repeats a held key every few tens of
 * milliseconds, so the time held is in the events themselves and no timer is needed. The key is kept
 * from the app while it is down; let go before [Shortcut.HOLD_MS] and the app gets its press back
 * ([Step.REPLAY]), held longer and the house opens ([Step.OPEN]) once.
 */
class Hold(private val holdMs: Long = Shortcut.HOLD_MS) {
    enum class Step { KEEP, OPEN, REPLAY }

    private var opened = false

    /** [down]: ACTION_DOWN; [repeat], [downTime], [eventTime]: the KeyEvent's. */
    fun step(down: Boolean, repeat: Int, downTime: Long, eventTime: Long): Step = when {
        down && repeat == 0 -> { opened = false; Step.KEEP }
        down -> if (!opened && eventTime - downTime >= holdMs) { opened = true; Step.OPEN } else Step.KEEP
        opened -> { opened = false; Step.KEEP }
        else -> Step.REPLAY
    }
}

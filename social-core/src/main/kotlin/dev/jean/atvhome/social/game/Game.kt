package dev.jean.atvhome.social.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

typealias PlayerId = String

/** One person in the room: a colour and a shape (shape alone tells people apart for the colour-blind). */
@Serializable
data class Player(val id: PlayerId, val name: String, val color: Int, val shape: Int)

/** What the table is doing, so the room knows when reactions and the host's lines are welcome. */
enum class Mood { SECRET, TALK, FOCUS }

enum class Category(val label: String) {
    DEDUCTION("Dedução e blefe"), TALK("Conversa e risadas"), DUO("Para dois"), COOP("Cooperativos"),
}

/** A choice the host makes on the game sheet: "Perguntas: 5 / 10 / 15". */
data class Setting(val key: String, val label: String, val options: List<String>, val default: Int = 0)

data class GameInfo(
    val id: String,
    val name: String,
    val tagline: String,
    val category: Category,
    val minPlayers: Int,
    val maxPlayers: Int,
    val idealMin: Int,
    val idealMax: Int,
    val minutes: Int,
    val difficulty: Int,
    /** "Todo mundo vota", "Só olha a TV": how much the phones are used. */
    val style: String,
    /** Three short steps, shown on the sheet before starting. */
    val howTo: List<String>,
    val settings: List<Setting> = emptyList(),
    /** The table wins or loses together (no rivalry points, no "winner"). */
    val coop: Boolean = false,
) {
    fun fits(players: Int) = players in minPlayers..maxPlayers
    fun ideal(players: Int) = players in idealMin..idealMax
}

class Setup(
    val players: List<Player>,
    val settings: Map<String, Int>,
    val random: Random,
    val now: Long,
    /** The game's content pack (questions, words, places…), already loaded. */
    val content: List<String>,
)

/** What a phone sends: a type from the shared vocabulary (see [Act]) and its data. */
data class Action(val type: String, val data: JsonObject = JsonObject(emptyMap()))

/** The shared action vocabulary: what each widget sends back. */
object Act {
    const val PICK = "pick"       // PickPlayer → {"id": "p3"}
    const val CHOOSE = "choose"   // Choose → {"key": "a"}
    const val READY = "ready"     // Ready → {}
    const val TEXT = "text"       // TextInput → {"value": "..."}
    const val SCALE = "scale"     // Scale → {"value": 0..10}
    const val BUTTON = "button"   // PhoneView.buttons → {"key": "..."}
}

sealed interface Step<out S> {
    data class Ok<S>(val state: S) : Step<S>
    data class Rejected(val why: String) : Step<Nothing>
}

/** What the TV's remote can do: advance, skip, or steer (dial, grid cursor, bomb). */
enum class HostCmd { CONTINUE, SKIP, LEFT, RIGHT, UP, DOWN }

/** Something worth remembering from a game, for the night's awards and the host's lines. */
data class Highlight(val kind: String, val who: PlayerId? = null, val n: Int = 0)

data class Outcome(
    /** Chips earned in this game, added to the night's board. */
    val chips: Map<PlayerId, Int>,
    val headline: String,
    val highlights: List<Highlight> = emptyList(),
    /** For cooperative games: did the whole table win (true) or lose (false)? Null for competitive games. */
    val coopWon: Boolean? = null,
) {
    /** Who won: everybody with chips when the table won together, nobody when it lost, else those with the most chips. */
    val winners: Set<PlayerId> get() = when (coopWon) {
        true -> chips.keys
        false -> emptySet()
        null -> chips.values.maxOrNull()?.takeIf { it > 0 }?.let { top -> chips.filterValues { it == top }.keys } ?: emptySet()
    }
}

/**
 * What the TV's OK button does right now. [hold] asks the person to hold OK for a second, so a stray
 * press cannot skip people who are still deciding. [secondary] is an optional second action.
 */
data class TvAction(val label: String, val cmd: HostCmd = HostCmd.CONTINUE, val hold: Boolean = false, val secondary: TvAction? = null)

/**
 * The rules of one game: pure functions over an immutable state, so every game can be tested on
 * the JVM with a fixed seed and a fake clock, and the room can run any of them the same way.
 * Time only enters through `now` (the game's own clock, which stops while the room is paused).
 */
interface Game<S : Any> {
    val info: GameInfo

    /** The button the TV shows for this moment of the game, or null when only the phones act. */
    fun tvAction(s: S): TvAction? = deadline(s)?.let { TvAction("Avançar", HostCmd.CONTINUE, hold = true) }

    /** Why the game cannot start with this content pack (no internet for the films, say), or null. */
    fun unavailable(content: List<String>): String? = null

    fun setup(ctx: Setup): S

    fun act(s: S, by: PlayerId, a: Action, now: Long): Step<S>

    fun host(s: S, cmd: HostCmd, now: Long): S

    /** The current phase's deadline passed. */
    fun timeout(s: S, now: Long): S

    fun deadline(s: S): Long?

    /** Names the phase, so an action sent for an earlier one is dropped instead of misapplied. */
    fun phase(s: S): String

    fun mood(s: S): Mood

    /** Only what [p] is allowed to see. */
    fun phone(s: S, p: PlayerId): PhoneView

    /** [p] left for good in the middle of the game. */
    fun left(s: S, p: PlayerId, now: Long): S

    /** Non-null once the game is finished. */
    fun over(s: S): Outcome?

    /** Strings that must never appear in [p]'s view (what only others may know); the contract test checks. */
    fun secrets(s: S, p: PlayerId): Set<String> = emptySet()
}

val SocialJson = Json { ignoreUnknownKeys = true; classDiscriminator = "w"; explicitNulls = false }

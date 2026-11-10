package dev.jean.atvhome.social.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a phone shows. The TV describes it as data and the phone page draws it, so a new game needs
 * no new JavaScript and a test can prove on the JVM that nothing secret is in it.
 */
@Serializable
data class PhoneView(
    val phase: String,
    val title: String? = null,
    /** A reminder kept on screen (hold to see): the secret word, the role. */
    val badge: Badge? = null,
    val body: Widget,
    /** Wall-clock end of the phase, in the TV's clock; null when there is none or the room is paused. */
    val endsAt: Long? = null,
    /** Extra actions under the body ("Acusar", "Já vi"): each sends Act.BUTTON with its key. */
    val buttons: List<Button> = emptyList(),
)

@Serializable
data class Button(val key: String, val label: String)

@Serializable
data class Cell(val label: String, val tone: String = "plain")

@Serializable
data class ManualPage(val title: String, val lines: List<String>)

@Serializable
data class Badge(val label: String, val value: String)

@Serializable
data class Option(val key: String, val label: String)

@Serializable
sealed interface Widget {
    /** "Olha pra TV". [react] shows the reaction stamps. */
    @Serializable @SerialName("wait")
    data class Wait(val text: String, val sub: String? = null, val react: Boolean = false) : Widget

    /** A sealed envelope: hold to open, let go to close. */
    @Serializable @SerialName("secret")
    data class Secret(val label: String, val value: String) : Widget

    /** Vote for one of the people in the room. */
    @Serializable @SerialName("pick")
    data class PickPlayer(val prompt: String, val options: List<PlayerId>, val chosen: PlayerId? = null) : Widget

    @Serializable @SerialName("choose")
    data class Choose(val prompt: String, val options: List<Option>, val chosen: String? = null) : Widget

    @Serializable @SerialName("ready")
    data class Ready(val text: String, val done: Boolean = false) : Widget

    /** A short text answer, sent as Act.TEXT. */
    @Serializable @SerialName("text")
    data class TextInput(val prompt: String, val hint: String? = null, val done: Boolean = false) : Widget

    /** A 0..10 slider between two poles, sent as Act.SCALE. */
    @Serializable @SerialName("scale")
    data class Scale(val prompt: String, val low: String, val high: String, val chosen: Int? = null) : Widget

    /** A read-only grid of coloured cells (a key card). [tone]: plain, good, bad, dark. */
    @Serializable @SerialName("grid")
    data class Grid(val cols: Int, val cells: List<Cell>, val caption: String? = null) : Widget

    /** Pages to read aloud from (the bomb manual). */
    @Serializable @SerialName("manual")
    data class Manual(val pages: List<ManualPage>) : Widget
}

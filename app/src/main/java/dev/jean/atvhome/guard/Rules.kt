package dev.jean.atvhome.guard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** assets/guard/rules.json: the words and limits the ad detector works with. Data, not code. */
@Serializable
data class Rules(
    val version: Int = 1,
    @SerialName("max_label_chars") val maxLabelChars: Int = 40,
    @SerialName("max_player_labels") val maxPlayerLabels: Int = 8,
    @SerialName("countdown_rule") val countdownRule: Boolean = true,
    @SerialName("tap_to_skip") val tapToSkip: Boolean = true,
    /** Kill switch for the screen reader (OCR), the only costly one. */
    @SerialName("screen_reader") val screenReader: Boolean = true,
    @SerialName("ad_words") val adWords: Map<String, List<String>> = emptyMap(),
    @SerialName("skip_words") val skipWords: Map<String, List<String>> = emptyMap(),
    @SerialName("filler_words") val fillerWords: List<String> = emptyList(),
    @SerialName("veto_words") val vetoWords: List<String> = emptyList(),
    @SerialName("ad_view_ids") val adViewIds: List<String> = emptyList(),
    @SerialName("skip_view_ids") val skipViewIds: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): Rules = json.decodeFromString(serializer(), text)
    }
}

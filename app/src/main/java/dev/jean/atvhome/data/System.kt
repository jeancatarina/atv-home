package dev.jean.atvhome.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A "keep watching" card that a streaming app published to Android TV's Watch Next channel. */
data class WatchNextItem(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val intentUri: String?,
    val progress: Float?,
)

/** An HDMI or other physical input of the TV. */
data class TvInput(val id: String, val label: String)

object TvSystem {

    private const val TAG = "TvSystem"
    private val WATCH_NEXT: Uri = Uri.parse("content://android.media.tv/watch_next_program")
    private val PREVIEW: Uri = Uri.parse("content://android.media.tv/preview_program")

    /**
     * Reads Watch Next. Android only shows a launcher the rows of other apps on some TVs; when it
     * doesn't, this returns an empty list and the row stays hidden.
     */
    suspend fun watchNext(context: Context): List<WatchNextItem> = programs(context, WATCH_NEXT, "last_engagement_time_utc_millis DESC")

    /** Cards apps publish to their home-screen channels ("Destaques" of Claro tv+, YouTube…). */
    suspend fun previewPrograms(context: Context): List<WatchNextItem> =
        programs(context, PREVIEW, null).filter { it.intentUri != null }.distinctBy { it.title }.take(20)

    private suspend fun programs(context: Context, uri: Uri, order: String?): List<WatchNextItem> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, null, null, null, order)?.use { c ->
                fun col(name: String) = c.getColumnIndex(name)
                val items = ArrayList<WatchNextItem>()
                while (c.moveToNext() && items.size < 20) {
                    val title = c.getString(col("title")) ?: continue
                    val duration = col("duration_millis").takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
                    val position = col("last_playback_position_millis").takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
                    val episode = col("episode_title").takeIf { it >= 0 }?.let { c.getString(it) }
                    val season = col("season_display_number").takeIf { it >= 0 }?.let { c.getString(it) }
                    val number = col("episode_display_number").takeIf { it >= 0 }?.let { c.getString(it) }
                    items += WatchNextItem(
                        packageName = c.getString(col("package_name")).orEmpty(),
                        title = title,
                        subtitle = listOfNotNull(
                            if (season != null && number != null) "T$season:E$number" else null,
                            episode,
                        ).joinToString(" · ").ifBlank { null },
                        image = (c.getString(col("poster_art_uri")) ?: c.getString(col("thumbnail_uri"))),
                        intentUri = c.getString(col("intent_uri")),
                        progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
                    )
                }
                items
            }.orEmpty().also { Log.i(TAG, "${uri.lastPathSegment}: ${it.size} cards") }
        }.onFailure { Log.i(TAG, "${uri.lastPathSegment} unavailable: ${it.message}") }.getOrDefault(emptyList())
    }

    fun open(context: Context, item: WatchNextItem): Boolean = runCatching {
        val intent = Intent.parseUri(item.intentUri ?: return false, Intent.URI_INTENT_SCHEME)
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Log.w(TAG, "open watch next", it) }.isSuccess

    /** HDMI and other passthrough inputs, by the names the user gave them in the TV settings. */
    fun inputs(context: Context): List<TvInput> = runCatching {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        manager.tvInputList.filter { it.isPassthroughInput && !runCatching { it.isHidden(context) }.getOrDefault(false) }.map { info ->
            TvInput(info.id, info.loadCustomLabel(context)?.toString()?.ifBlank { null } ?: info.loadLabel(context).toString())
        }.sortedWith(compareBy<TvInput> { if (it.label.contains("HDMI", true)) 0 else 1 }.thenBy { it.label })
    }.onFailure { Log.w(TAG, "inputs", it) }.getOrDefault(emptyList())

    fun switchTo(context: Context, input: TvInput): Boolean = runCatching {
        val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Log.w(TAG, "switch input", it) }.isSuccess

    fun openSettings(context: Context, action: String = Settings.ACTION_SETTINGS): Boolean = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Starts the screensaver now (the system's "dream" activity). */
    fun startScreensaver(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    fun trailer(context: Context, youtubeKey: String): Boolean {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeKey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeKey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(web) }.isSuccess
    }

}

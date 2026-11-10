package dev.jean.atvhome.guard

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Exists only so MediaSessionManager.getActiveSessions accepts us (tools/ad-guard-setup.sh allows
 * it). Android checks that the listener is allowed, not that it is bound, so it lets go of the
 * binding at once: otherwise the system would keep the guard process alive forever.
 */
class SessionListener : NotificationListenerService() {
    override fun onListenerConnected() { requestUnbind() }
}

object SessionReader {
    /** ExoPlayer's MediaSessionConnector and Media3 set this to 1 while an ad plays. */
    private const val KEY_AD = "android.media.metadata.ADVERTISEMENT"
    private var warned = false

    /** Null when the listener is not allowed or [pkg] has no media session. */
    fun read(context: Context, pkg: String): Reading? = runCatching {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val controller = manager.getActiveSessions(ComponentName(context, SessionListener::class.java))
            .firstOrNull { it.packageName == pkg } ?: return null
        Reading(Source.SESSION, adFlag = controller.metadata?.getLong(KEY_AD) == 1L)
    }.onFailure {
        // SecurityException = listener not allowed: reader 1 stays silent, said once in the log
        if (!warned) { warned = true; Log.w("AdGuard", "media sessions unavailable", it) }
    }.getOrNull()
}

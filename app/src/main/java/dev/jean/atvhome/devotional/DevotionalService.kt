package dev.jean.atvhome.devotional

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.jean.atvhome.R

/** Keeps the app process, and with it the devotional site, alive while the TV is in standby. */
class DevotionalService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Devocional", NotificationManager.IMPORTANCE_MIN))
        val note = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(R.drawable.banner).setContentTitle("Devocional").setContentText("O site do devocional está no ar na rede de casa").setOngoing(true).build()
        startForeground(ID, note)
        DevotionalHost.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        DevotionalHost.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "devotional"
        private const val ID = 7
        fun start(context: Context) {
            runCatching {
                val i = Intent(context, DevotionalService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            }
        }
    }
}

/** Starts the site when the TV boots or the app updates, before anyone opens the launcher. */
class DevotionalBoot : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DevotionalService.start(context)
        }
    }
}

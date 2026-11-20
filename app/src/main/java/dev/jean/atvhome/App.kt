package dev.jean.atvhome

import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import dev.jean.atvhome.data.Apps
import dev.jean.atvhome.data.Catalog
import dev.jean.atvhome.data.MyList
import dev.jean.atvhome.data.TmdbClient
import dev.jean.atvhome.work.DailyWorker
import java.util.concurrent.TimeUnit

class App : Application(), SingletonImageLoader.Factory {

    lateinit var catalog: Catalog private set
    lateinit var apps: Apps private set
    lateinit var myList: MyList private set
    lateinit var deepLinks: dev.jean.atvhome.data.DeepLinks private set
    /** "Casa", only when tools/tuya-setup.sh pushed the house's tuya.json. */
    var home: dev.jean.atvhome.data.SmartHome? = null; private set
    @Volatile private var returnHomeOnWake = false
    var onWake: (() -> Unit)? = null

    fun consumeReturnHomeOnWake(): Boolean {
        val had = returnHomeOnWake
        returnHomeOnWake = false
        return had
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DREAMING_STARTED -> {
                    dev.jean.atvhome.casa.DreamState.set(context, true)
                    dev.jean.atvhome.data.AdShield.setDreaming(context, true)
                }
                Intent.ACTION_DREAMING_STOPPED -> {
                    dev.jean.atvhome.casa.DreamState.set(context, false)
                    dev.jean.atvhome.data.AdShield.setDreaming(context, false)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    dev.jean.atvhome.casa.DreamState.set(context, false)
                    dev.jean.atvhome.data.AdShield.setDreaming(context, false)
                    returnHomeOnWake = true
                }
                Intent.ACTION_SCREEN_ON -> {
                    dev.jean.atvhome.casa.DreamState.set(context, false)
                    dev.jean.atvhome.data.AdShield.setDreaming(context, false)
                    if (returnHomeOnWake) onWake?.invoke()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The ad guard's process (docs/anuncios/SINAIS.md) and the film overlay's use none of the launcher's state
        val process = processName().orEmpty()
        if (process.endsWith(":guard") || process.endsWith(":casa")) return
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_DREAMING_STARTED); addAction(Intent.ACTION_DREAMING_STOPPED)
        })
        catalog = Catalog(TmdbClient(BuildConfig.TMDB_TOKEN, cacheDir))
        apps = Apps(this)
        myList = MyList(this)
        deepLinks = dev.jean.atvhome.data.DeepLinks(cacheDir)
        home = dev.jean.atvhome.data.SmartHome.forLauncher(this)
        // Unlisted while Home is in front so Compose pays nothing for accessibility semantics, and
        // listed whenever another app or the screensaver is active (granted by setup scripts)
        dev.jean.atvhome.data.AdShield.manageHouse(this)
        // "Sem anúncios": the guard is listed only while another app plays sound
        dev.jean.atvhome.data.AdShield.watch(this)
        // Devocional: the site on the home network, up with the app process even in standby
        dev.jean.atvhome.devotional.DevotionalService.start(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    private fun processName(): String? =
        if (Build.VERSION.SDK_INT >= 28) getProcessName()
        else getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }?.processName

    /** Images are decoded at the size they're drawn and the memory cache is capped at 64 MB (2.3 GB TV). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L * 1024 * 1024).build() }
        .allowRgb565(true)
        .crossfade(150)
        .build()
}

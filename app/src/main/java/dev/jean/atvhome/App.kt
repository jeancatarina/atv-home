package dev.jean.atvhome

import android.app.ActivityManager
import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        // The ad guard's process (docs/anuncios/SINAIS.md) uses none of the launcher's state
        if (processName()?.endsWith(":guard") == true) return
        catalog = Catalog(TmdbClient(BuildConfig.TMDB_TOKEN, cacheDir))
        apps = Apps(this)
        myList = MyList(this)
        deepLinks = dev.jean.atvhome.data.DeepLinks(cacheDir)
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
        .crossfade(350)
        .build()
}

package dev.jean.atvhome

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        catalog = Catalog(TmdbClient(BuildConfig.TMDB_TOKEN, cacheDir))
        apps = Apps(this)
        myList = MyList(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Images are decoded at the size they're drawn and the memory cache is capped at 64 MB (2.3 GB TV). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L * 1024 * 1024).build() }
        .crossfade(350)
        .build()
}

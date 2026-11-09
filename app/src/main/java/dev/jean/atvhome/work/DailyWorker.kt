package dev.jean.atvhome.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.jean.atvhome.App
import dev.jean.atvhome.data.Format
import dev.jean.atvhome.data.ImdbRatings
import dev.jean.atvhome.data.MediaType
import dev.jean.atvhome.data.ProviderMatch

/**
 * Once a day: refresh IMDb's ratings and check My List — a title that arrived on a service the user
 * has gets "Agora no {serviço}", a series with an episode this week gets "Novo ep. {dia}".
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as App
        ImdbRatings.ensureFresh(app)
        val catalog = app.catalog
        val installed = app.apps.load().map { it.packageName to it.label }
        val providers = runCatching { catalog.providersBR() }.getOrDefault(emptyList())
        val mine = ProviderMatch.match(providers, installed).keys
        for (entry in app.myList.entries.value) {
            val details = catalog.details(entry.title) ?: continue
            val now = details.providers.map { it.id }
            val arrived = details.providers.firstOrNull { it.id in mine && it.id !in entry.knownProviders }
            val badge = when {
                arrived != null -> "Agora no ${shortName(arrived.name)}"
                entry.title.type == MediaType.TV -> Format.episodeBadge(details.nextEpisodeDate, details.lastEpisodeDate)
                else -> entry.badge
            }
            app.myList.update { if (it.title.key == entry.title.key) it.copy(knownProviders = now, badge = badge) else it }
        }
        return Result.success()
    }

    private fun shortName(name: String) = name
        .replace("Amazon Prime Video", "Prime")
        .replace("Disney Plus", "Disney+")
        .replace(Regex(" Standard with Ads| Basic with Ads"), "")
}

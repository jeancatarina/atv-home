package dev.jean.atvhome.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class MediaType(val path: String) { MOVIE("movie"), TV("tv") }

/** A movie or series as lists return it (trending, discover, search). */
@Serializable
data class Title(
    val id: Int,
    val type: MediaType,
    val name: String,
    val overview: String = "",
    val year: Int? = null,
    val backdrop: String? = null,
    val poster: String? = null,
    val genreIds: List<Int> = emptyList(),
    val tmdbRating: Double = 0.0,
    val tmdbVotes: Int = 0,
) {
    val key get() = "${type.path}:$id"
}

data class CastMember(val id: Int, val name: String, val character: String?, val photo: String?)

/** Where a title streams in Brazil (subscription only: rent and buy aren't "watch now"). */
data class Provider(val id: Int, val name: String, val logo: String?)

data class Details(
    val title: Title,
    val imdbId: String?,
    val logo: String?,
    val genres: List<String>,
    val runtime: Int?,
    val seasons: Int?,
    val certification: String?,
    val tagline: String?,
    val cast: List<CastMember>,
    val trailerKey: String?,
    val providers: List<Provider>,
    val recommendations: List<Title>,
    val nextEpisodeDate: String?,
    val lastEpisodeDate: String?,
)

object Parse {

    /** TMDB leaves some series genres in English even with language=pt-BR. */
    private val GENRE_PT = mapOf(
        "Action & Adventure" to "Ação e Aventura",
        "Sci-Fi & Fantasy" to "Ficção científica e Fantasia",
        "War & Politics" to "Guerra e Política",
        "Kids" to "Infantil",
        "News" to "Notícias",
        "Soap" to "Novela",
        "Talk" to "Talk show",
        "Reality" to "Reality show",
    )

    fun title(o: JsonObject, fallbackType: MediaType? = null): Title? {
        val type = when (o.str("media_type")) {
            "movie" -> MediaType.MOVIE
            "tv" -> MediaType.TV
            null -> fallbackType
            else -> null // people in multi-search
        } ?: return null
        val name = o.str("title") ?: o.str("name") ?: return null
        val date = o.str("release_date") ?: o.str("first_air_date")
        return Title(
            id = o.int("id") ?: return null,
            type = type,
            name = name,
            overview = o.str("overview").orEmpty(),
            year = date?.take(4)?.toIntOrNull(),
            backdrop = o.str("backdrop_path"),
            poster = o.str("poster_path"),
            genreIds = o.array("genre_ids").mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() },
            tmdbRating = o.double("vote_average") ?: 0.0,
            tmdbVotes = o.int("vote_count") ?: 0,
        )
    }

    fun titles(o: JsonObject, fallbackType: MediaType? = null): List<Title> =
        o.objects("results").mapNotNull { title(it, fallbackType) }

    /** One `append_to_response` call worth of details. */
    fun details(o: JsonObject, type: MediaType): Details? {
        val base = title(o, type) ?: return null
        val genres = o.objects("genres").mapNotNull { it.str("name") }.map { GENRE_PT[it] ?: it }
        val withGenres = base.copy(genreIds = o.objects("genres").mapNotNull { it.int("id") })
        val logos = o.obj("images")?.objects("logos").orEmpty()
        // Portuguese logo first, then English, then a textless one; skip SVGs (Coil can't draw them)
        val logo = (logos.firstOrNull { it.str("iso_639_1") == "pt" } ?: logos.firstOrNull { it.str("iso_639_1") == "en" } ?: logos.firstOrNull())
            ?.str("file_path")?.takeUnless { it.endsWith(".svg") }
        val videos = o.obj("videos")?.objects("results").orEmpty().filter { it.str("site") == "YouTube" }
        val trailer = (videos.firstOrNull { it.str("type") == "Trailer" && it.str("iso_639_1") == "pt" }
            ?: videos.firstOrNull { it.str("type") == "Trailer" }
            ?: videos.firstOrNull { it.str("type") == "Teaser" })?.str("key")
        val br = o.obj("watch/providers")?.obj("results")?.obj("BR")
        val providers = br?.objects("flatrate").orEmpty().mapNotNull { p ->
            Provider(p.int("provider_id") ?: return@mapNotNull null, p.str("provider_name") ?: return@mapNotNull null, p.str("logo_path"))
        }
        val certification = when (type) {
            MediaType.MOVIE -> o.obj("release_dates")?.objects("results").orEmpty()
                .firstOrNull { it.str("iso_3166_1") == "BR" }?.objects("release_dates").orEmpty()
                .firstNotNullOfOrNull { it.str("certification") }
            MediaType.TV -> o.obj("content_ratings")?.objects("results").orEmpty()
                .firstOrNull { it.str("iso_3166_1") == "BR" }?.str("rating")
        }
        val cast = o.obj("credits")?.objects("cast").orEmpty().take(15).mapNotNull { c ->
            CastMember(c.int("id") ?: return@mapNotNull null, c.str("name") ?: return@mapNotNull null, c.str("character"), c.str("profile_path"))
        }
        return Details(
            title = withGenres,
            imdbId = o.str("imdb_id") ?: o.obj("external_ids")?.str("imdb_id"),
            logo = logo,
            genres = genres,
            runtime = o.int("runtime") ?: o.array("episode_run_time").firstNotNullOfOrNull {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            },
            seasons = o.int("number_of_seasons"),
            certification = certification,
            tagline = o.str("tagline"),
            cast = cast,
            trailerKey = trailer,
            providers = providers,
            recommendations = o.obj("recommendations")?.let { titles(it, type) }.orEmpty(),
            nextEpisodeDate = o.obj("next_episode_to_air")?.str("air_date"),
            lastEpisodeDate = o.obj("last_episode_to_air")?.str("air_date"),
        )
    }
}

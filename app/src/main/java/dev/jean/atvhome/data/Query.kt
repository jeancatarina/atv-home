package dev.jean.atvhome.data

import java.text.Normalizer
import java.time.LocalDate

/** Filters for discover, from chips or from a sentence. Null means "any". */
data class Filters(
    val type: MediaType = MediaType.MOVIE,
    val genres: Set<Genre> = emptySet(),
    val providerIds: Set<Int> = emptySet(),
    val maxRuntime: Int? = null,
    val minRuntime: Int? = null,
    val minImdb: Double? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val person: String? = null,
) {
    val isEmpty get() = genres.isEmpty() && providerIds.isEmpty() && maxRuntime == null && minRuntime == null &&
        minImdb == null && fromYear == null && toYear == null && person == null
}

/**
 * Explore's categories. Most are TMDB genres, whose ids differ between movies and series for a few;
 * the rest are a TMDB keyword ("anime", "superhero") or a country of origin, the same for both kinds.
 * [only]: the one kind it exists for (reality shows are series, stand-up specials are films).
 */
enum class Genre(
    val label: String,
    val movieId: Int?,
    val tvId: Int?,
    val keyword: Int? = null,
    val country: String? = null,
    val only: MediaType? = null,
) {
    COMEDY("Comédia", 35, 35),
    ACTION("Ação", 28, 10759),
    SUPERHERO("Super-heróis", null, null, keyword = 9715),
    ADVENTURE("Aventura", 12, 10759),
    THRILLER("Suspense", 53, 9648),
    HORROR("Terror", 27, null),
    DRAMA("Drama", 18, 18),
    ROMANCE("Romance", 10749, null),
    SCIFI("Ficção científica", 878, 10765),
    FANTASY("Fantasia", 14, 10765),
    ANIMATION("Animação", 16, 16),
    ANIME("Anime", null, null, keyword = 210024),
    DOCUMENTARY("Documentário", 99, 99),
    TRUE_STORY("Fatos reais", null, null, keyword = 9672),
    FAMILY("Família", 10751, 10751),
    KIDS("Infantil", null, 10762, only = MediaType.TV),
    CRIME("Crime", 80, 80),
    TRUE_CRIME("True crime", null, null, keyword = 33722),
    MYSTERY("Mistério", 9648, 9648),
    WAR("Guerra", 10752, 10768),
    WESTERN("Faroeste", 37, 37),
    HISTORY("História", 36, null),
    MUSIC("Musical", 10402, null),
    REALITY("Reality show", null, 10764, only = MediaType.TV),
    SOAP("Novela", null, 10766, only = MediaType.TV),
    STANDUP("Stand-up", null, null, keyword = 9716, only = MediaType.MOVIE),
    KOREAN("Coreanos", null, null, country = "KR"),
    BRAZILIAN("Nacionais", null, null, country = "BR"),
    CHRISTMAS("Natal", null, null, keyword = 207317),
    TIME_TRAVEL("Viagem no tempo", null, null, keyword = 4379),
    DYSTOPIA("Distopia", null, null, keyword = 4565),
    HEIST("Assalto", null, null, keyword = 10051),
    SPY("Espionagem", null, null, keyword = 470),
    ZOMBIE("Zumbis", null, null, keyword = 12377),
    SPORTS("Esportes", null, null, keyword = 6075);

    fun id(type: MediaType) = if (type == MediaType.MOVIE) movieId else tvId

    /** Whether it means something for [type]: its genre there, or a keyword or country, which fit both. */
    fun fits(type: MediaType) = (only == null || only == type) && (id(type) != null || keyword != null || country != null)

    /** Whether Explore shows its chip with [type] chosen: also when it is the other kind's own (it switches). */
    fun shown(type: MediaType) = fits(type) || only != null
}

/**
 * Turns a Portuguese sentence into [Filters] with plain rules (no paid AI):
 * "comédia curta na netflix com nota alta", "suspense dos anos 90 no prime",
 * "série de terror", "filmes com o wagner moura", "até 1h30".
 */
object QueryParser {

    data class Result(val filters: Filters, val leftover: String)

    private val genreWords: List<Pair<Regex, Genre>> = listOf(
        // The specific ones first: "true crime" before "crime", "historia real" before "historia"
        "true crime" to Genre.TRUE_CRIME,
        "fatos? reais|historia real|baseado em fatos" to Genre.TRUE_STORY,
        "super[- ]?hero|super[- ]?heroi" to Genre.SUPERHERO,
        "\\banimes?\\b" to Genre.ANIME,
        "stand-?up" to Genre.STANDUP,
        "novelas?\\b" to Genre.SOAP,
        "dorama|k-?drama|corean" to Genre.KOREAN,
        "nacionais|nacional\\b|brasileir" to Genre.BRAZILIAN,
        "\\bnatal\\b|natalin" to Genre.CHRISTMAS,
        "viagem no tempo" to Genre.TIME_TRAVEL,
        "distopi" to Genre.DYSTOPIA,
        "\\bassaltos?\\b|roubo" to Genre.HEIST,
        "espiona|\\bespia" to Genre.SPY,
        "zumbi" to Genre.ZOMBIE,
        "esporte|futebol" to Genre.SPORTS,
        "comedi|engracad|\\brir\\b|humor" to Genre.COMEDY,
        "\\bacao\\b|tiroteio|\\bluta" to Genre.ACTION,
        "aventura" to Genre.ADVENTURE,
        "suspense|thriller|tensao|tenso" to Genre.THRILLER,
        "terror|horror|\\bmedo\\b|assustador" to Genre.HORROR,
        "drama|emocion|emocao|chorar" to Genre.DRAMA,
        "romance|romantic|amor\\b" to Genre.ROMANCE,
        "ficcao|sci-?fi|espacial|espaco\\b" to Genre.SCIFI,
        "fantasia" to Genre.FANTASY,
        "animacao|animad|desenho" to Genre.ANIMATION,
        "documentari" to Genre.DOCUMENTARY,
        "familia|criancas|infantil|kids" to Genre.FAMILY,
        "\\bcrime|policia|mafia" to Genre.CRIME,
        "misterio|investigac" to Genre.MYSTERY,
        "guerra" to Genre.WAR,
        "faroeste|western|velho oeste" to Genre.WESTERN,
        "historic|historia\\b|biografi" to Genre.HISTORY,
        "musical" to Genre.MUSIC,
        "\\breality(?: shows?)?\\b|\\brealities\\b" to Genre.REALITY,
    ).map { (pattern, genre) -> Regex(pattern) to genre }

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    /**
     * [providers]: installed services as (provider id, name) so "netflix", "prime", "max" match
     * the user's apps.
     */
    fun parse(sentence: String, providers: List<Provider>, today: LocalDate = LocalDate.now()): Result {
        var text = " ${normalize(sentence)} "
        var filters = Filters()
        fun consume(regex: Regex): MatchResult? = regex.find(text)?.also { text = text.replaceRange(it.range, " ") }

        if (consume(Regex("\\bseries?\\b|\\bseriado|temporada")) != null) filters = filters.copy(type = MediaType.TV)
        consume(Regex("\\bfilmes?\\b"))

        val genres = LinkedHashSet<Genre>()
        for ((regex, genre) in genreWords) if (consume(regex) != null) genres += genre
        filters = filters.copy(genres = genres)
        genres.firstNotNullOfOrNull { it.only }?.let { filters = filters.copy(type = it) }

        val providerIds = HashSet<Int>()
        for (provider in providers) {
            val aliases = providerAliases(provider.name)
            for (alias in aliases) {
                if (consume(Regex("\\b${Regex.escape(alias)}\\b")) != null) providerIds += provider.id
            }
        }
        filters = filters.copy(providerIds = providerIds)

        // Duration: "até 1h30", "menos de 2 horas", "até 90 minutos", "curto", "longo"
        consume(Regex("(?:ate|menos de|no maximo|max) (\\d+)\\s*h(?:oras?)?\\s*(?:e\\s*)?(\\d+)?\\s*(?:min(?:utos)?)?"))?.let {
            val h = it.groupValues[1].toInt()
            val m = it.groupValues[2].toIntOrNull() ?: 0
            filters = filters.copy(maxRuntime = h * 60 + m)
        }
        consume(Regex("(?:ate|menos de|no maximo) (\\d+)\\s*min(?:utos)?"))?.let {
            filters = filters.copy(maxRuntime = it.groupValues[1].toInt())
        }
        if (consume(Regex("\\bcurt[oa]s?\\b|curtinh|rapid[oa]")) != null) filters = filters.copy(maxRuntime = filters.maxRuntime ?: 100)
        if (consume(Regex("\\blong[oa]s?\\b|epico")) != null) filters = filters.copy(minRuntime = 140)

        // Rating: "nota acima de 8", "nota 7.5", "nota alta", "bem avaliado", "os melhores"
        consume(Regex("nota (?:acima de |maior que |minima |de )?(\\d+(?:[.,]\\d)?)"))?.let {
            filters = filters.copy(minImdb = it.groupValues[1].replace(',', '.').toDouble())
        }
        if (consume(Regex("nota alta|bem avaliad|aclamad|\\bmelhores\\b|obra.prima")) != null) {
            filters = filters.copy(minImdb = filters.minImdb ?: 7.5)
        }

        // Time: "anos 80", "dos 90", "anos 2000", "recente", "lançamento", "clássico"
        consume(Regex("(?:anos|dos|da decada de) (\\d{2,4})"))?.let {
            val n = it.groupValues[1].toInt()
            val start = when {
                n >= 1900 -> n / 10 * 10
                n < 30 -> 2000 + n
                else -> 1900 + n
            }
            filters = filters.copy(fromYear = start, toYear = start + 9)
        }
        if (consume(Regex("\\b(?:recentes?|novos?|novidades?|lancamentos?)\\b")) != null) {
            filters = filters.copy(fromYear = today.year - 2, toYear = null)
        }
        if (consume(Regex("\\b(?:classicos?|antigos?)\\b")) != null) filters = filters.copy(fromYear = null, toYear = 1989)

        // Person last, once "com nota alta" and the like are gone: "com o wagner moura", "do diretor ..."
        consume(Regex(" (?:com o|com a|com|estrelad[oa] por|do diretor|da diretora|dirigido por) ([a-z]+(?: [a-z]+){0,3})\\s*$"))
            ?.let { filters = filters.copy(person = it.groupValues[1].trim()) }

        val leftover = text.replace(Regex("\\b(de|do|da|dos|das|no|na|nos|nas|um|uma|uns|umas|com|e|o|a|os|as|pra|para|quero|ver|assistir|algo|alguma coisa|bom|boa|bons|que|seja|tipo)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return Result(filters, leftover)
    }

    /** How people say a service out loud: "Amazon Prime Video" → prime video, prime, amazon. */
    fun providerAliases(name: String): List<String> {
        val n = normalize(name)
        val aliases = mutableListOf(n)
        when {
            "prime" in n -> aliases += listOf("prime video", "prime", "amazon")
            "disney" in n -> aliases += listOf("disney plus", "disney+", "disney")
            "hbo" in n || n == "max" -> aliases += listOf("hbo max", "hbo", "max")
            "apple" in n -> aliases += listOf("apple tv", "apple")
            "globo" in n -> aliases += listOf("globoplay", "globo")
            "claro" in n -> aliases += listOf("claro tv", "claro")
            "netflix" in n -> aliases += listOf("netflix")
            "paramount" in n -> aliases += listOf("paramount")
        }
        return aliases.distinct().sortedByDescending { it.length }
    }
}

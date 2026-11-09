/*
 * Ported from Sofadrop (https://github.com/rfmarcos/projectivy-plugin-sofadrop), Apache License 2.0,
 * Copyright rfmarcos. Changes: the index also keeps the vote count, so suggestions can skip titles
 * whose rating rests on a handful of votes.
 */
package dev.jean.atvhome.data

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** An IMDb rating (0.0–10.0) and how many people voted. */
data class ImdbRating(val rating: Double, val votes: Int)

/**
 * IMDb ratings without any account, from IMDb's official dataset (title.ratings.tsv.gz, ~9 MB,
 * updated daily; free for personal, non-commercial use).
 */
object ImdbRatings {

    private const val TAG = "ImdbRatings"
    private const val URL = "https://datasets.imdbws.com/title.ratings.tsv.gz"
    private const val MAX_AGE = 2 * 86_400_000L
    private const val RETRY_AFTER = 3_600_000L
    private const val MAX_VOTES = 0xFFFFFF

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    // Sorted entries: (numeric id shl 32) or (votes shl 8) or (rating x10); ~13 MB for the dataset
    @Volatile private var index: LongArray? = null
    private var indexDate = 0L
    private var failedAt = 0L

    private fun file(context: Context) = File(context.filesDir, "imdb/title.ratings.tsv.gz")

    /** When the dataset in use was downloaded; 0 if it never was. */
    fun downloadedAt(context: Context): Long = file(context).lastModified()

    /** True once [ensureFresh] has loaded a dataset into memory. */
    val isLoaded get() = index != null

    /**
     * Rating of "tt0137523", or null when IMDb has none or the dataset isn't loaded yet. Never
     * touches the disk, so it is safe on the main thread; [ensureFresh] does the loading.
     */
    fun rating(imdbId: String?): ImdbRating? {
        val id = imdbId?.removePrefix("tt")?.toIntOrNull() ?: return null
        return find(index ?: return null, id)
    }

    /** Downloads a fresh copy when ours is older than two days, then loads it. Call off the main thread. */
    @Synchronized
    fun ensureFresh(context: Context) {
        val file = file(context)
        if (System.currentTimeMillis() - file.lastModified() > MAX_AGE &&
            System.currentTimeMillis() - failedAt > RETRY_AFTER
        ) {
            runCatching { download(file) }
                .onFailure { failedAt = System.currentTimeMillis(); Log.w(TAG, "IMDb dataset download failed", it) }
        }
        load(file)
    }

    private fun download(file: File) {
        val temp = File(file.parentFile, "${file.name}.part").apply { parentFile?.mkdirs() }
        http.newCall(Request.Builder().url(URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("IMDb dataset HTTP ${response.code}")
            temp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
        }
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) throw IOException("Could not save the IMDb dataset")
        }
        Log.i(TAG, "IMDb dataset downloaded (${file.length() / 1024} KB)")
    }

    private fun load(file: File): LongArray? {
        if (!file.exists()) return null
        if (index != null && indexDate == file.lastModified()) return index
        index = null // let the old copy go before building the new one
        val start = System.currentTimeMillis()
        return runCatching { GZIPInputStream(file.inputStream(), 1 shl 16).use(::parse) }
            .onFailure { Log.w(TAG, "Unreadable IMDb dataset", it); file.delete() }
            .getOrNull()
            ?.also {
                index = it
                indexDate = file.lastModified()
                Log.i(TAG, "${it.size} IMDb ratings loaded in ${System.currentTimeMillis() - start} ms")
            }
    }

    /**
     * "tt0137523\t8.8\t2675558" lines (after a header) into the sorted index. Read byte by byte:
     * 1.7 million lines as Strings take half a minute on a TV-class CPU.
     */
    internal fun parse(input: InputStream): LongArray {
        var entries = LongArray(2_000_000)
        var size = 0
        val buffer = ByteArray(1 shl 16)
        var header = true
        var field = 0
        var id = 0L
        var rating = 0L
        var votes = 0L
        var decimals = false
        var valid = true
        fun endLine() {
            // Ratings come with one decimal ("8.8", "10.0"): their digits are the rating x10
            if (!header && valid && field >= 1 && id > 0) {
                if (size == entries.size) entries = entries.copyOf(size * 3 / 2)
                val tenths = if (decimals) rating else rating * 10
                entries[size++] = (id shl 32) or (minOf(votes, MAX_VOTES.toLong()) shl 8) or tenths
            }
            header = false; field = 0; id = 0; rating = 0; votes = 0; decimals = false; valid = true
        }
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            for (i in 0 until read) {
                val c = buffer[i].toInt()
                when {
                    c == '\n'.code -> endLine()
                    header || !valid -> Unit
                    c == '\r'.code -> Unit
                    c == '\t'.code -> field++
                    field == 0 -> if (c in '0'.code..'9'.code) id = id * 10 + (c - '0'.code) else if (c != 't'.code) valid = false
                    field == 1 -> if (c in '0'.code..'9'.code) rating = rating * 10 + (c - '0'.code) else if (c == '.'.code) decimals = true else valid = false
                    field == 2 -> if (c in '0'.code..'9'.code) votes = votes * 10 + (c - '0'.code)
                }
            }
        }
        endLine()
        return entries.copyOf(size).apply { sort() }
    }

    internal fun find(index: LongArray, id: Int): ImdbRating? {
        var low = 0
        var high = index.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midId = index[mid] ushr 32
            when {
                midId < id -> low = mid + 1
                midId > id -> high = mid - 1
                else -> {
                    val entry = index[mid]
                    return ImdbRating((entry and 0xFF) / 10.0, ((entry ushr 8) and MAX_VOTES.toLong()).toInt())
                }
            }
        }
        return null
    }
}

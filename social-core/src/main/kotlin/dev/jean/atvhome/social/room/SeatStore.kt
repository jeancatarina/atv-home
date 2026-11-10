package dev.jean.atvhome.social.room

import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.game.SocialJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class SavedSeat(val token: String, val player: Player, val spectator: Boolean, val slot: Int, val device: String? = null)

@Serializable
data class SavedRoom(val key: String, val savedAt: Long, val nextId: Int, val seats: List<SavedSeat>)

/** Where the room's key and seats survive a restart of the launcher. The game itself is never saved. */
interface SeatStore {
    fun load(): SavedRoom?
    fun save(room: SavedRoom)
}

class FileSeatStore(private val file: File) : SeatStore {
    override fun load(): SavedRoom? = runCatching { SocialJson.decodeFromString<SavedRoom>(file.readText()) }.getOrNull()

    @Synchronized
    override fun save(room: SavedRoom) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(SocialJson.encodeToString(room))
            tmp.renameTo(file)
        }
    }
}

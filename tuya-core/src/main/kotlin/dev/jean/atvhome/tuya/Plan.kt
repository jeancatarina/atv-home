package dev.jean.atvhome.tuya

import kotlinx.serialization.Serializable

/**
 * The house's floor plan for the model ("Maquete"): floors from the ground up, each with its rooms
 * as rectangles on one footprint ([width] × [depth], any unit: metres or squares). Written by hand
 * in casa-plan.json (tools/casa-plan.example.json), embedded in tuya.json by the setup; without it
 * the model lays the rooms out on one floor by itself ([auto]).
 */
@Serializable
data class Plan(val width: Float, val depth: Float, val floors: List<Floor>) {
    /** Room names on the plan that the Tuya app does not know (a typo, or a room renamed in the app). */
    fun unknownRooms(known: Collection<String>): List<String> =
        floors.flatMap { f -> f.rooms.map { it.room } }.filter { it !in known }.distinct()

    companion object {
        /** No plan written: every room with lights on one floor, in rows of four. */
        fun auto(rooms: List<String>): Plan {
            val cols = 4
            val rows = (rooms.size + cols - 1) / cols
            val w = 3f
            val d = 3f
            return Plan(
                width = cols * w,
                depth = maxOf(1, rows) * d,
                floors = listOf(Floor("Casa", rooms.mapIndexed { i, r -> RoomBox(r, (i % cols) * w, (i / cols) * d, w, d) })),
            )
        }
    }
}

/**
 * One floor. [open]: rooms without walls between them (an integrated living and kitchen).
 * [outdoor]: a terrace: a deck with a glass railing instead of walls.
 */
@Serializable
data class Floor(
    val name: String,
    val rooms: List<RoomBox>,
    val open: Boolean = false,
    val outdoor: Boolean = false,
    /** What the model draws besides the rooms: a deck, a lawn, a jacuzzi, the water tank… */
    val features: List<Feature> = emptyList(),
)

/**
 * Something the model draws that is not a room and has no lights: [kind] is one of deck, grass,
 * ceramic (finishes on the floor), jacuzzi, tank (the water tank's housing), stairs (where the stairs
 * come up), flight (a flight of stairs), bench (a built-in bench), skylight, planter. A kind the
 * model does not know is skipped.
 */
@Serializable
data class Feature(val kind: String, val x: Float, val y: Float, val w: Float, val d: Float)

/** A room's rectangle on the footprint: [x] along the width, [y] along the depth, from the back corner. */
@Serializable
data class RoomBox(val room: String, val x: Float, val y: Float, val w: Float, val d: Float)

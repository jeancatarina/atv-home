package dev.jean.atvhome.tuya

/** The house panel's filters: "Acesas", "Luzes", "Cenas", "Todas". */
enum class Show(val label: String) { LIT("Acesas"), LIGHTS("Luzes"), SCENES("Cenas"), ALL("Todas") }

/**
 * What the house panel shows and switches, the same on Home and over a film. Pure: the app passes the
 * hub's state in.
 */
object Panel {
    fun isOn(light: Light, states: Map<String, Boolean>) = light.keys.any { states[it] == true }

    /** [lights] through a filter and, when [room] is set, one room; the setup's order (rooms, names) is kept. */
    fun filter(lights: List<Light>, states: Map<String, Boolean>, show: Show, room: String? = null): List<Light> =
        lights.filter { l ->
            (room == null || l.room == room) && when (show) {
                Show.LIT -> isOn(l, states)
                Show.LIGHTS -> l.kind == Kind.LIGHT
                Show.SCENES -> l.kind == Kind.SCENE
                Show.ALL -> true
            }
        }

    fun rooms(lights: List<Light>): List<String> = lights.map { it.room }.distinct()

    /**
     * The frames that switch a group: off turns off every key that is on (spares and scenes too: a
     * key left on is a light left on); on turns on only the lights ([Kind.LIGHT]), never a scene.
     * One map of keys per switch, so each switch is one frame.
     */
    fun switching(lights: List<Light>, states: Map<String, Boolean>, on: Boolean): Map<String, Map<Int, Boolean>> =
        lights.asSequence()
            .filter { if (on) it.kind == Kind.LIGHT else true }
            .flatMap { l -> l.dps.asSequence().filter { (states[keyOf(l.cid, it)] == true) != on }.map { l.cid to it } }
            .distinct()
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dps) -> dps.associateWith { on } }
}

/**
 * The two scenes. The TV's room is the first room of the setup (the app's order), where people
 * watch from.
 */
object Scenes {
    /** "Cinema": the TV's whole floor goes dark, the open living and kitchen with it; the rest of the house stays. */
    fun cinema(lights: List<Light>, plan: Plan?): List<Light> {
        val tv = lights.firstOrNull()?.room ?: return emptyList()
        val floor = plan?.floors?.firstOrNull { f -> f.rooms.any { it.room == tv } } ?: return lights.filter { it.room == tv }
        val rooms = floor.rooms.map { it.room }.toSet()
        return lights.filter { it.room in rooms }
    }

    /**
     * "Boa noite": every room, farthest from the TV first, the TV's room last, so the dark comes
     * through the house towards where you are. Rooms off the plan go first.
     */
    fun goodNight(lights: List<Light>, plan: Plan?): List<List<Light>> {
        val byRoom = lights.groupBy { it.room }
        val tv = lights.firstOrNull()?.room ?: return emptyList()
        fun where(room: String): Triple<Int, Float, Float>? {
            plan ?: return null
            plan.floors.forEachIndexed { k, f -> f.rooms.firstOrNull { it.room == room }?.let { return Triple(k, it.x + it.w / 2, it.y + it.d / 2) } }
            return null
        }
        val home = where(tv)
        fun distance(room: String): Float {
            if (room == tv) return 0f
            if (home == null) return 1f
            val at = where(room) ?: return Float.MAX_VALUE
            val dx = at.second - home.second; val dy = at.third - home.third
            return kotlin.math.abs(at.first - home.first) * 100f + kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return byRoom.keys.sortedByDescending(::distance).map { byRoom.getValue(it) }
    }
}

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

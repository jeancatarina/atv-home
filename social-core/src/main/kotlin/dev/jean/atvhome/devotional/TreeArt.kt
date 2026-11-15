package dev.jean.atvhome.devotional

/** One shape of the couple's tree, in a 120 x 120 box. [color] is 0xRRGGBB. */
sealed interface Shape { val color: Int }
data class Disc(val cx: Float, val cy: Float, val r: Float, override val color: Int) : Shape
data class Block(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, override val color: Int) : Shape

/**
 * The tree in six stages, as plain shapes: the server turns them into an SVG for the phones and the
 * TV draws the very same list, so the two never disagree.
 */
object TreeArt {
    const val STAGES = 6
    // Muted on purpose: one brass note (the fruit) over quiet greens and a bark brown
    private const val GROUND = 0x5A5347
    private const val TRUNK = 0x7A6650
    private const val LEAF = 0x6B8764
    private const val LEAF_LIGHT = 0x88A07F
    const val FRUIT = 0xC9A96B

    fun stage(n: Int): List<Shape> {
        val ground = Block(24f, 103.5f, 72f, 1.6f, 0.8f, GROUND)
        return listOf(ground) + when (n.coerceIn(0, STAGES - 1)) {
            0 -> listOf(Disc(60f, 100f, 12f, TRUNK), Disc(52f, 86f, 8f, LEAF), Disc(68f, 86f, 8f, LEAF_LIGHT), Disc(60f, 78f, 6f, LEAF))
            1 -> trunk(4f, 24f) + listOf(Disc(53f, 76f, 8f, LEAF), Disc(67f, 76f, 8f, LEAF_LIGHT))
            2 -> trunk(5f, 36f) + listOf(Disc(50f, 66f, 11f, LEAF), Disc(70f, 66f, 11f, LEAF_LIGHT), Disc(60f, 56f, 12f, LEAF))
            3 -> trunk(8f, 46f) + listOf(Disc(46f, 54f, 17f, LEAF), Disc(74f, 54f, 17f, LEAF_LIGHT), Disc(60f, 38f, 18f, LEAF))
            4 -> trunk(10f, 54f) + canopy()
            else -> trunk(10f, 54f) + canopy() + fruit()
        }
    }

    private fun trunk(w: Float, h: Float): List<Shape> = listOf(Block(60f - w / 2, 104f - h, w, h, w / 2, TRUNK))
    private fun canopy(): List<Shape> = listOf(
        Disc(40f, 54f, 19f, LEAF), Disc(80f, 54f, 19f, LEAF_LIGHT), Disc(60f, 32f, 21f, LEAF), Disc(60f, 56f, 20f, LEAF_LIGHT),
    )
    private fun fruit(): List<Shape> = listOf(
        Disc(44f, 48f, 3.6f, FRUIT), Disc(74f, 40f, 3.6f, FRUIT), Disc(60f, 62f, 3.6f, FRUIT), Disc(82f, 58f, 3.6f, FRUIT), Disc(38f, 60f, 3.6f, FRUIT),
    )

    fun svg(n: Int): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">""")
        stage(n).forEach { s ->
            val hex = "#%06x".format(s.color)
            when (s) {
                is Disc -> append("""<circle cx="${s.cx}" cy="${s.cy}" r="${s.r}" fill="$hex"/>""")
                is Block -> append("""<rect x="${s.x}" y="${s.y}" width="${s.w}" height="${s.h}" rx="${s.radius}" fill="$hex"/>""")
            }
        }
        append("</svg>")
    }
}

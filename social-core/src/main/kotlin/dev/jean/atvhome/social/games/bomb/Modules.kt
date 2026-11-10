package dev.jean.atvhome.social.games.bomb

import kotlin.random.Random

/** One module of the bomb. Rules and manual text come from the same definitions below, so they cannot disagree. */
sealed interface Module {
    val solved: Boolean
    val title: String
}

val WIRE_COLORS = listOf("vermelho", "azul", "amarelo", "branco", "preto")

data class Wires(val wires: List<String>, val cut: Set<Int> = emptySet(), override val solved: Boolean = false) : Module { override val title get() = "Fios" }

data class PushButton(
    val color: String, val word: String, val held: Boolean = false, val heldAt: Long = 0, override val solved: Boolean = false,
) : Module { override val title get() = "Botão" }

data class Symbols(val shown: List<Int>, val order: List<Int>, val pressed: List<Int> = emptyList(), override val solved: Boolean = false) : Module { override val title get() = "Símbolos" }

data class Maze(val id: Int, val pos: Int, val end: Int, override val solved: Boolean = false) : Module { override val title get() = "Labirinto" }

/** What the defuser can read off the bomb itself. */
data class Edgework(val serial: String, val batteries: Int, val car: Boolean, val frk: Boolean) {
    val serialOdd: Boolean get() = (serial.last().digitToIntOrNull() ?: 0) % 2 == 1
}

// --- Fios ---------------------------------------------------------------------------------------------

class WireRule(val whenText: String?, val test: (List<String>, Edgework) -> Boolean, val cutText: String, val index: (List<String>) -> Int)

object WireRules {
    private fun List<String>.n(c: String) = count { it == c }
    private fun first(c: String): (List<String>) -> Int = { it.indexOf(c) }
    private val ordinals = listOf("o primeiro", "o segundo", "o terceiro", "o quarto", "o quinto", "o sexto")

    val byCount: Map<Int, List<WireRule>> = mapOf(
        3 to listOf(
            WireRule("não houver fio vermelho", { w, _ -> w.n("vermelho") == 0 }, "o segundo fio") { 1 },
            WireRule("o último fio for branco", { w, _ -> w.last() == "branco" }, "o último fio") { it.lastIndex },
            WireRule(null, { _, _ -> true }, "o primeiro fio") { 0 },
        ),
        4 to listOf(
            WireRule("houver mais de um fio azul e o último dígito do número de série for ímpar", { w, e -> w.n("azul") > 1 && e.serialOdd }, "o último fio azul") { it.lastIndexOf("azul") },
            WireRule("o último fio for amarelo e não houver fio vermelho", { w, _ -> w.last() == "amarelo" && w.n("vermelho") == 0 }, "o primeiro fio") { 0 },
            WireRule("houver exatamente um fio azul", { w, _ -> w.n("azul") == 1 }, "o primeiro fio") { 0 },
            WireRule(null, { _, _ -> true }, "o segundo fio") { 1 },
        ),
        5 to listOf(
            WireRule("o último fio for preto e o último dígito do número de série for ímpar", { w, e -> w.last() == "preto" && e.serialOdd }, "o quarto fio") { 3 },
            WireRule("houver exatamente um fio vermelho e mais de um amarelo", { w, _ -> w.n("vermelho") == 1 && w.n("amarelo") > 1 }, "o primeiro fio") { 0 },
            WireRule("não houver fio preto", { w, _ -> w.n("preto") == 0 }, "o segundo fio") { 1 },
            WireRule(null, { _, _ -> true }, "o primeiro fio") { 0 },
        ),
        6 to listOf(
            WireRule("não houver fio amarelo e o último dígito do número de série for ímpar", { w, e -> w.n("amarelo") == 0 && e.serialOdd }, "o terceiro fio") { 2 },
            WireRule("houver exatamente um fio amarelo e mais de um branco", { w, _ -> w.n("amarelo") == 1 && w.n("branco") > 1 }, "o quarto fio") { 3 },
            WireRule("não houver fio vermelho", { w, _ -> w.n("vermelho") == 0 }, "o último fio") { it.lastIndex },
            WireRule(null, { _, _ -> true }, "o quarto fio") { 3 },
        ),
    )

    fun correct(wires: List<String>, e: Edgework): Int = byCount.getValue(wires.size).first { it.test(wires, e) }.index(wires)

    fun manual(): List<String> = byCount.flatMap { (count, rules) ->
        listOf("Com $count fios:") + rules.mapIndexed { i, r -> (if (r.whenText == null) "Em qualquer outro caso" else (if (i == 0) "Se " else "Senão, se ") + r.whenText) + ": corte ${r.cutText}." }
    }
}

// --- Botão --------------------------------------------------------------------------------------------

class ButtonRule(val whenText: String?, val test: (PushButton, Edgework) -> Boolean, val hold: Boolean)

object ButtonRules {
    val colors = listOf("azul", "branco", "amarelo", "vermelho")
    val words = listOf("ABORTAR", "SEGURAR", "DETONAR", "APERTAR")

    val rules = listOf(
        ButtonRule("o botão for azul e a palavra for ABORTAR", { b, _ -> b.color == "azul" && b.word == "ABORTAR" }, true),
        ButtonRule("houver mais de 1 bateria e a palavra for DETONAR", { b, e -> e.batteries > 1 && b.word == "DETONAR" }, false),
        ButtonRule("o botão for branco e a luz CAR estiver acesa", { b, e -> b.color == "branco" && e.car }, true),
        ButtonRule("houver 3 ou mais baterias e a luz FRK estiver acesa", { _, e -> e.batteries >= 3 && e.frk }, false),
        ButtonRule("o botão for amarelo", { b, _ -> b.color == "amarelo" }, true),
        ButtonRule("o botão for vermelho e a palavra for SEGURAR", { b, _ -> b.color == "vermelho" && b.word == "SEGURAR" }, false),
        ButtonRule(null, { _, _ -> true }, true),
    )
    /** While holding, a coloured strip lights up; let go when the timer shows this digit. */
    val releaseDigit = mapOf("azul" to 4, "branco" to 1, "amarelo" to 5)
    const val OTHER_DIGIT = 1
    const val TAP_MS = 600L

    fun shouldHold(b: PushButton, e: Edgework) = rules.first { it.test(b, e) }.hold
    fun digitFor(strip: String) = releaseDigit[strip] ?: OTHER_DIGIT
    /** The strip colour shown while holding (a fixed function of the button, so the defuser can read it out). */
    fun stripOf(b: PushButton) = colors[(colors.indexOf(b.color) + words.indexOf(b.word)) % colors.size]

    fun manual(): List<String> = listOf("Olhe a cor do botão e a palavra nele. Aplique a primeira regra que servir:") +
        rules.mapIndexed { i, r -> (if (r.whenText == null) "Em qualquer outro caso" else (if (i == 0) "Se " else "Senão, se ") + r.whenText) + ": " + if (r.hold) "SEGURE o botão." else "APERTE E SOLTE na hora (menos de um segundo)." } +
        listOf("Ao segurar, uma faixa colorida acende. Solte quando o cronômetro mostrar, em qualquer posição:", "faixa azul: o dígito 4 · faixa branca: o dígito 1 · faixa amarela: o dígito 5 · outra cor: o dígito 1.")
}

// --- Símbolos -----------------------------------------------------------------------------------------

object SymbolTable {
    val glyphs = listOf("☀", "☾", "★", "♠", "♣", "♥", "♦", "♫", "☂", "✿", "⚓", "✈")
    val columns: List<List<Int>> = listOf(
        listOf(0, 1, 2, 3, 4, 5, 6),
        listOf(7, 0, 6, 10, 11, 5, 9),
        listOf(8, 9, 10, 2, 7, 4, 1),
    )

    /** Four glyphs from one column that no other column holds all of, and the order to press them in. */
    fun draw(random: Random): Pair<List<Int>, List<Int>> {
        while (true) {
            val col = columns.random(random)
            val pick = col.shuffled(random).take(4)
            if (columns.count { c -> pick.all { it in c } } == 1) return pick to pick.sortedBy { col.indexOf(it) }
        }
    }

    fun manual(): List<String> = listOf("Os 4 símbolos do módulo estão todos em UMA destas colunas. Aperte na ordem em que aparecem na coluna, de cima para baixo:") +
        columns.mapIndexed { i, c -> "Coluna ${i + 1}: " + c.joinToString("  ") { glyphs[it] } }
}

// --- Labirinto ----------------------------------------------------------------------------------------

/** A 6×6 maze (perfect: one path between any two cells), made from its id so the bomb and the manual share it. */
object Mazes {
    const val SIZE = 6
    const val COUNT = 4

    /** open[c] = the cells you can walk to from cell c. */
    fun open(id: Int): Map<Int, Set<Int>> {
        val r = Random(1000 + id)
        val links = HashMap<Int, MutableSet<Int>>()
        val seen = HashSet<Int>()
        fun neighbours(c: Int) = listOfNotNull(
            (c - SIZE).takeIf { c >= SIZE }, (c + SIZE).takeIf { c + SIZE < SIZE * SIZE },
            (c - 1).takeIf { c % SIZE != 0 }, (c + 1).takeIf { c % SIZE != SIZE - 1 },
        )
        fun visit(c: Int) {
            seen += c
            neighbours(c).shuffled(r).forEach { n -> if (n !in seen) { links.getOrPut(c) { mutableSetOf() } += n; links.getOrPut(n) { mutableSetOf() } += c; visit(n) } }
        }
        visit(0)
        return links
    }

    /** The two cells marked with circles, which tell the mazes apart. */
    fun markers(id: Int): Pair<Int, Int> {
        val r = Random(2000 + id)
        val cells = (0 until SIZE * SIZE).shuffled(r)
        return cells[0] to cells[1]
    }

    fun canMove(id: Int, from: Int, to: Int) = to in open(id)[from].orEmpty()

    /** ASCII art for the manual, with ● on the marker cells. */
    fun draw(id: Int): List<String> {
        val open = open(id); val (m1, m2) = markers(id)
        val rows = ArrayList<String>()
        rows += "█".repeat(SIZE * 2 + 1)
        for (y in 0 until SIZE) {
            val line = StringBuilder("█")
            val below = StringBuilder("█")
            for (x in 0 until SIZE) {
                val c = y * SIZE + x
                line.append(if (c == m1 || c == m2) "●" else " ")
                line.append(if (x < SIZE - 1 && (c + 1) in open[c].orEmpty()) " " else "█")
                below.append(if (y < SIZE - 1 && (c + SIZE) in open[c].orEmpty()) " " else "█")
                below.append("█")
            }
            rows += line.toString(); rows += below.toString()
        }
        return rows.map { "¦$it" }
    }

    fun manual(): List<String> = listOf("Dois círculos ● no módulo dizem qual labirinto é este. Guie o desarmador do ponto de partida até o alvo, passando só pelos espaços:") +
        (0 until COUNT).flatMap { listOf("Labirinto ${it + 1}") + draw(it) }
}

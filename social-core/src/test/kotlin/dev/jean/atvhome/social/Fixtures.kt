package dev.jean.atvhome.social

import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.room.Clock

class FakeClock(var t: Long = 1_000_000L) : Clock {
    override fun now() = t
    fun advance(ms: Long) { t += ms }
}

fun people(n: Int) = (1..n).map { Player("p$it", "Pessoa $it", it % 12, it % 6) }

val QUESTIONS = (1..40).map { "Quem é mais provável de fazer a coisa número $it?" }

package dev.jean.atvhome.social.room

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One daemon thread that rings the room when its next deadline is due; a new request replaces the old one. */
class ExecutorTimer(private val clock: Clock = Clock { System.currentTimeMillis() }) : Timer, AutoCloseable {

    private val executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "room-timer").apply { isDaemon = true } }
    private var pending: ScheduledFuture<*>? = null

    @Synchronized
    override fun at(whenMs: Long, run: () -> Unit) {
        pending?.cancel(false)
        pending = runCatching { executor.schedule({ run() }, maxOf(0L, whenMs - clock.now()), TimeUnit.MILLISECONDS) }.getOrNull()
    }

    override fun close() { executor.shutdownNow() }
}

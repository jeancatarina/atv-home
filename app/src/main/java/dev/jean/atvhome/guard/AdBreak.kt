package dev.jean.atvhome.guard

/** What the service must do right now. [active] = mute and cover. */
data class Decision(val active: Boolean, val click: Label? = null, val keep: List<Label> = emptyList())

/**
 * Turns verdicts into a steady "in a break / not in a break", so one bad read neither starts nor
 * ends a break. Fails open: a break never lasts more than [MAX_BREAK_MS], and after that the guard
 * stays out for [COOLDOWN_MS].
 */
class AdBreak {
    private var positives = 0
    private var negatives = 0
    private var since = 0L        // 0 = not in a break
    private var blockedUntil = 0L
    private var lastClick = 0L

    val inBreak: Boolean get() = since != 0L

    /** When the open break started; 0 when there is none. */
    val startedAt: Long get() = since

    fun on(verdict: Verdict, now: Long): Decision {
        if (now < blockedUntil) return Decision(false)
        if (verdict == Verdict.None) { positives = 0; negatives++ } else { negatives = 0; positives++ }
        if (!inBreak && positives >= ENTER_READS) since = now
        if (inBreak && negatives >= EXIT_READS) since = 0L
        if (inBreak && now - since > MAX_BREAK_MS) { reset(); blockedUntil = now + COOLDOWN_MS; return Decision(false) }
        // The skip button is pressed on the first read: nothing to gain by waiting
        val click = (verdict as? Verdict.Skippable)?.button?.takeIf { now - lastClick >= CLICK_GAP_MS }
        if (click != null) lastClick = now
        val keep = when (verdict) { is Verdict.Ad -> verdict.keep; is Verdict.Skippable -> verdict.keep; Verdict.None -> emptyList() }
        return Decision(inBreak, click, keep)
    }

    /** The app in front changed, or the guard was switched off. */
    fun reset() { positives = 0; negatives = 0; since = 0L; blockedUntil = 0L; lastClick = 0L }

    companion object {
        const val ENTER_READS = 2
        const val EXIT_READS = 2
        const val MAX_BREAK_MS = 240_000L
        const val COOLDOWN_MS = 60_000L
        const val CLICK_GAP_MS = 2_000L
    }
}

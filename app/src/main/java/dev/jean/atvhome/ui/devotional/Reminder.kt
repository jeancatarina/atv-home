package dev.jean.atvhome.ui.devotional

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.devotional.DevotionalHost
import dev.jean.atvhome.devotional.Reminder
import dev.jean.atvhome.devotional.reminder
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private val Brass = Color(0xFFC9A96B)

/** What Home should remind about right now (see [reminder] for the rules), or null. One calculation for the chip and the dot. */
@Composable
fun rememberDevotionalReminder(): Reminder? {
    val context = LocalContext.current
    val board = remember { DevotionalHost.board(context) }
    val version by DevotionalHost.changes.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalDateTime.now() } }
    return remember(version, now) { board.reminder(now.toLocalDate(), System.currentTimeMillis(), now.hour) }
}

/**
 * A quiet chip under the clock on Home: no focus and no motion beyond a soft cross-fade when the
 * message changes, and nothing at all when there is nothing to say.
 */
@Composable
fun DevotionalReminder(reminder: Reminder?, modifier: Modifier = Modifier) {
    AnimatedContent(
        reminder, modifier = modifier, label = "reminder",
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { r ->
        if (r != null) {
            val (icon, text) = describe(r)
            Row(
                Modifier
                    .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = if (r is Reminder.Prize) 0.28f else 0.12f), RoundedCornerShape(50))
                    .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(15.dp), tint = Brass)
                Spacer(Modifier.width(7.dp))
                Text(text, style = Tv.Meta, color = if (r is Reminder.Prize) Color.White else Tv.Secondary, maxLines = 1)
            }
        }
    }
}

private fun describe(r: Reminder): Pair<ImageVector, String> = when (r) {
    is Reminder.Prize -> Icons.Rounded.CardGiftcard to "Prêmio liberado  ·  ${r.text}"
    is Reminder.Called -> Icons.Rounded.Notifications to "${r.by} lembrou ${r.who}"
    is Reminder.Missing -> Icons.Rounded.MenuBook to "Devocional  ·  " + if (r.names.size == 1) "falta ${r.names.first()}" else "faltam ${r.names.joinToString(" e ")}"
}

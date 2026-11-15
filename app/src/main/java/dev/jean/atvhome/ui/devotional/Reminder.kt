package dev.jean.atvhome.ui.devotional

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.jean.atvhome.devotional.DevotionalHost
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** The reminder starts in the evening: earlier, nobody has had the chance yet and it would only nag. */
private const val REMIND_FROM_HOUR = 18

/** Names of who has not done today's devotional yet, once it is time to remind; empty otherwise. */
@Composable
fun rememberDevotionalPending(): List<String> {
    val context = LocalContext.current
    val board = remember { DevotionalHost.board(context) }
    val version by DevotionalHost.changes.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalDateTime.now() } }
    return remember(version, now) {
        if (now.hour < REMIND_FROM_HOUR) emptyList() else board.pending(now.toLocalDate()).map { it.name }
    }
}

/** A quiet glass chip under the clock on Home: no focus, no motion, gone as soon as everyone has checked in. */
@Composable
fun DevotionalReminder(names: List<String>, modifier: Modifier = Modifier) {
    AnimatedVisibility(names.isNotEmpty(), modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier
                .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.MenuBook, null, Modifier.size(15.dp), tint = Color(0xFFC9A96B))
            Spacer(Modifier.width(7.dp))
            Text(
                "Devocional  ·  " + if (names.size == 1) "falta ${names.first()}" else "faltam ${names.joinToString(" e ")}",
                style = Tv.Meta, color = Tv.Secondary, maxLines = 1,
            )
        }
    }
}

package dev.jean.atvhome.social.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.jean.atvhome.social.ui.Crown
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.games.mostlikely.MostLikelyState
import dev.jean.atvhome.social.games.mostlikely.Stage as Step
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.social.ui.Avatar
import dev.jean.atvhome.social.ui.PersonChip
import dev.jean.atvhome.social.ui.Stage
import dev.jean.atvhome.social.ui.Stamp
import dev.jean.atvhome.social.ui.TimeBar
import dev.jean.atvhome.social.ui.personColor
import dev.jean.atvhome.social.ui.rememberCountdown
import dev.jean.atvhome.ui.theme.Tv

/** The public side of "Quem é Mais Provável?": the question, who has voted, then the reveal. */
@Composable
fun MostLikelyStage(s: MostLikelyState, snap: RoomSnapshot, endsAt: Long?) {
    when (s.stage) {
        Step.VOTE -> Voting(s, snap, endsAt)
        Step.REVEAL -> Revealed(s)
        Step.DONE -> Unit // the result panel is shared by every game
    }
}

@Composable
private fun Voting(s: MostLikelyState, snap: RoomSnapshot, endsAt: Long?) {
    val (left, share) = rememberCountdown(endsAt)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Pergunta ${s.round + 1} de ${s.questions.size}" + if (s.double) "  ·  vale dobro" else "",
            style = Stage.Body, color = Tv.Tertiary,
        )
        Spacer(Modifier.height(14.dp))
        Text(s.question, style = Stage.Hero, color = Tv.Primary, modifier = Modifier.widthIn(max = 760.dp), maxLines = 3)
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.players.forEach { p ->
                val online = snap.players.firstOrNull { it.player.id == p.id }?.online ?: true
                PersonChip(p, dim = !online, mark = if (s.votes.cast.containsKey(p.id)) "✓" else null)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("${s.votes.cast.size} de ${s.votes.voters.size} votaram  ·  ${left.value} s", style = Stage.Body, color = Tv.Secondary)
        Spacer(Modifier.height(16.dp))
        TimeBar(share.value, Modifier.width(520.dp))
    }
}

/**
 * The reveal: the most voted under a spotlight on the left (face, crown, a stamp with the votes or
 * "UNÂNIME"), and on the right the tally, whose bars grow one after another.
 */
@Composable
private fun Revealed(s: MostLikelyState) {
    val reveal = s.reveal ?: return
    val ranked = s.players.sortedByDescending { reveal.tally[it.id] ?: 0 }.filter { (reveal.tally[it.id] ?: 0) > 0 }
    val top = reveal.tally.values.maxOrNull() ?: 0
    val leaders = reveal.leaders.mapNotNull { id -> s.players.firstOrNull { it.id == id } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(s.question, style = Stage.Title.copy(fontSize = 24.sp, lineHeight = 29.sp), color = Tv.Secondary, modifier = Modifier.widthIn(max = 780.dp), maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (leaders.isEmpty()) Text("Ninguém votou", style = Stage.Hero, color = Tv.Primary)
        else Row(Modifier.widthIn(max = 820.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(44.dp)) {
            Spotlight(leaders, top, reveal.unanimous != null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ranked.take(6).forEachIndexed { i, p -> TallyRow(p, reveal.tally[p.id] ?: 0, top, p.id in reveal.leaders, i) }
            }
        }
    }
}

@Composable
private fun Spotlight(leaders: List<Player>, votes: Int, unanimous: Boolean) {
    val first = leaders.first()
    val pop = remember(first.id) { Animatable(0.4f) }
    LaunchedEffect(first.id) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(200.dp, 150.dp).background(Brush.radialGradient(listOf(personColor(first).copy(alpha = 0.45f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                Crown(34.dp)
                Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) { leaders.take(3).forEach { Avatar(it, if (leaders.size == 1) 104.dp else 76.dp, ring = true) } }
            }
        }
        Text(leaders.joinToString(" e ") { it.name }, style = Stage.Hero.copy(fontSize = 34.sp, lineHeight = 38.sp), color = Tv.Primary, maxLines = 2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Stamp(if (unanimous) "UNÂNIME" else if (votes == 1) "1 VOTO" else "$votes VOTOS", color = if (unanimous) Color(0xFFFF453A) else Stage.Gold)
        // The spotlight is also a hot seat: whoever the table picked gets a few seconds to defend themselves
        Spacer(Modifier.height(14.dp))
        Text(
            if (leaders.size > 1) "Os dois se defendem!" else if (unanimous) "Unânime! Se defende!" else "Se defende!",
            style = Stage.Body.copy(fontSize = 18.sp), color = Tv.Secondary, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TallyRow(p: Player, votes: Int, top: Int, leader: Boolean, index: Int) {
    val grow = remember(p.id, votes) { Animatable(0f) }
    LaunchedEffect(p.id, votes) { delay(250L + index * 140L); grow.animateTo(votes.toFloat() / maxOf(top, 1), tween(650)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(p, 36.dp)
        Text(p.name, style = Stage.Body, color = if (leader) Tv.Primary else Tv.Secondary, modifier = Modifier.width(120.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(20.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(grow.value).height(20.dp).background(Brush.horizontalGradient(listOf(personColor(p).copy(alpha = 0.75f), personColor(p))), RoundedCornerShape(50)))
        }
        Text("$votes", style = Stage.Title.copy(fontSize = 22.sp), color = if (leader) Stage.Gold else Tv.Primary, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
    }
}

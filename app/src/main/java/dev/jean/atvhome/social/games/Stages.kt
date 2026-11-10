@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.jean.atvhome.social.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.tv.material3.Text
import coil3.request.crossfade
import dev.jean.atvhome.social.game.Player
import dev.jean.atvhome.social.games.blackstory.BStage
import dev.jean.atvhome.social.games.bomb.BombStage
import dev.jean.atvhome.social.games.bomb.BombState
import dev.jean.atvhome.social.games.bomb.Maze
import dev.jean.atvhome.social.games.bomb.Mazes
import dev.jean.atvhome.social.games.bomb.PushButton
import dev.jean.atvhome.social.games.bomb.ButtonRules
import dev.jean.atvhome.social.games.bomb.SymbolTable
import dev.jean.atvhome.social.games.bomb.Symbols
import dev.jean.atvhome.social.games.bomb.Wires
import dev.jean.atvhome.social.games.blackstory.BlackStoryState
import dev.jean.atvhome.social.games.couple.CStage
import dev.jean.atvhome.social.games.couple.CoupleState
import dev.jean.atvhome.social.games.mafia.MStage
import dev.jean.atvhome.social.games.mafia.MafiaState
import dev.jean.atvhome.social.games.mafia.NIGHT_ORDER
import dev.jean.atvhome.social.games.tribunal.TrStage
import dev.jean.atvhome.social.games.tribunal.TribunalState
import dev.jean.atvhome.social.games.film5.FiStage
import dev.jean.atvhome.social.games.film5.FilmState
import dev.jean.atvhome.social.games.frequency.FStage
import dev.jean.atvhome.social.games.frequency.FrequencyState
import dev.jean.atvhome.social.games.insider.IStage
import dev.jean.atvhome.social.games.insider.InsiderState
import dev.jean.atvhome.social.games.missions.MissionsState
import dev.jean.atvhome.social.games.mostlikely.MostLikelyState
import dev.jean.atvhome.social.games.psychologist.PStage
import dev.jean.atvhome.social.games.psychologist.PsychologistState
import dev.jean.atvhome.social.games.spyfall.SStage
import dev.jean.atvhome.social.games.spyfall.SpyState
import dev.jean.atvhome.social.games.twotruths.TStage
import dev.jean.atvhome.social.games.twotruths.TwoTruthsState
import dev.jean.atvhome.social.games.undercover.Role
import dev.jean.atvhome.social.games.undercover.UStage
import dev.jean.atvhome.social.games.undercover.UndercoverState
import dev.jean.atvhome.social.room.RoomSnapshot
import dev.jean.atvhome.social.ui.Avatar
import dev.jean.atvhome.social.ui.PersonChip
import dev.jean.atvhome.social.ui.Stage
import dev.jean.atvhome.social.ui.Stamp
import dev.jean.atvhome.social.ui.TimeBar
import dev.jean.atvhome.social.ui.personColor
import dev.jean.atvhome.social.ui.rememberCountdown
import dev.jean.atvhome.ui.theme.Tv
import kotlinx.coroutines.delay

/** The public side of every game: one branch per game's state. */
@Composable
fun StageBody(state: Any, snap: RoomSnapshot, endsAt: Long?) {
    when (state) {
        is MostLikelyState -> MostLikelyStage(state, snap, endsAt)
        is UndercoverState -> UndercoverStage(state, snap, endsAt)
        is FrequencyState -> FrequencyStage(state, endsAt)
        is CoupleState -> CoupleStage(state, endsAt)
        is TwoTruthsState -> TwoTruthsStage(state, endsAt)
        is SpyState -> SpyfallStage(state, endsAt)
        is InsiderState -> InsiderStage(state, endsAt)
        is PsychologistState -> PsychologistStage(state, endsAt)
        is BlackStoryState -> BlackStoryStage(state, endsAt)
        is MissionsState -> MissionsStage(state, endsAt)
        is MafiaState -> MafiaStage(state, endsAt)
        is TribunalState -> TribunalStage(state, endsAt)
        is FilmState -> FilmStage(state, endsAt)
        is BombState -> BombStageView(state, endsAt)
        else -> Text("Este jogo ainda não tem tela", style = Stage.Body, color = Tv.Tertiary)
    }
}

/** What a finished game adds above the shared result panel (who was who, what the answer was). */
@Composable
fun FinishedDetails(state: Any) {
    val lines: List<String> = when (state) {
        is UndercoverState -> listOf("Civis: “${state.civilWord}”  ·  Infiltrados: “${state.infiltratorWord}”") +
            state.players.map { "${it.name}: ${roleLabel(state.roles[it.id])}" }.chunked(3).map { it.joinToString("   ·   ") }
        is SpyState -> listOf("Local: ${state.location}", "Espião: ${state.name(state.spy)}")
        is InsiderState -> listOf("Palavra: ${state.word}", "Mestre: ${state.name(state.master)}  ·  Infiltrado: ${state.name(state.insider)}")
        is PsychologistState -> listOf("Regra: ${state.rule}")
        is BlackStoryState -> listOf(state.mystery.solution)
        is BombState -> listOf(if (state.stage == BombStage.WON) "Módulos: ${state.modules.size} de ${state.modules.size}" else "Módulos desarmados: ${state.solvedCount} de ${state.modules.size}")
        is FilmState -> state.films.map { f -> f.title + if (f.where.isNotBlank()) "  ·  disponível no ${f.where}" else "" }
        is MafiaState -> listOf("Cartas do centro: " + state.center.joinToString { it.label }) +
            state.players.map { "${it.name}: ${state.current[it.id]?.label}" }.chunked(3).map { it.joinToString("   ·   ") }
        is TribunalState -> listOf(state.proofs().joinToString("   ·   ")) + state.players.map { "${it.name}: ${state.alibis[it.id]?.place}, ${state.alibis[it.id]?.thing}, ${state.alibis[it.id]?.time}" }.take(8)
        else -> emptyList()
    }
    lines.forEach { Text(it, style = Stage.Body, color = Tv.Secondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 760.dp)) }
}

private fun roleLabel(r: Role?) = when (r) { Role.CIVIL -> "civil"; Role.INFILTRADO -> "infiltrado"; Role.BRANCO -> "Sr. Branco"; null -> "?" }

private fun clock(ms: Long): String = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/** "5:32" counting down to [endsAt]. */
@Composable
private fun BigClock(endsAt: Long?, label: String? = null) {
    val (left, _) = rememberCountdown(endsAt)
    if (endsAt != null) Text((label?.let { "$it  " } ?: "") + clock(left.value * 1000L), style = Stage.Title, color = if (left.value <= 10) Color(0xFFFF453A) else Tv.Primary)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun Heading(small: String?, big: String, sub: String? = null) {
    if (small != null) Text(small, style = Stage.Body, color = Tv.Tertiary)
    Text(big, style = Stage.Hero, color = Tv.Primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 800.dp), maxLines = 3)
    if (sub != null) Text(sub, style = Stage.Body, color = Tv.Secondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 760.dp))
}

@Composable
private fun People(players: List<Player>, mark: (Player) -> String? = { null }, dim: (Player) -> Boolean = { false }) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        players.forEach { PersonChip(it, dim = dim(it), mark = mark(it)) }
    }
}

// --- Infiltrados ---------------------------------------------------------------------------------

@Composable
private fun UndercoverStage(s: UndercoverState, snap: RoomSnapshot, endsAt: Long?) {
    val alive = s.players.filter { it.id in s.alive }
    Centered {
        when (s.stage) {
            UStage.WORDS -> {
                Heading("Rodada 1", "Olha sua palavra no celular", "Segura pra ver e toca em “Já vi”")
                People(s.players, mark = { if (it.id in s.ready) "✓" else null })
            }
            UStage.CLUES -> {
                val p = s.players.first { it.id == s.speakerId }
                Text("Rodada ${s.round}  ·  uma pista por pessoa", style = Stage.Body, color = Tv.Tertiary)
                Avatar(p, 88.dp, ring = true)
                Text("${p.name} diz uma pista", style = Stage.Hero, color = Tv.Primary)
                val (_, share) = rememberCountdown(endsAt)
                TimeBar(share.value, Modifier.width(420.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.alive.forEachIndexed { i, id -> s.players.firstOrNull { it.id == id }?.let { q -> Avatar(q, 34.dp, dim = i < s.speaker, ring = i == s.speaker) } }
                }
            }
            UStage.VOTE -> {
                Heading(if (s.revote) "Desempate" else "Rodada ${s.round}", "Quem é o infiltrado?", "Votem pelo celular")
                People(alive, mark = { if (s.votes.cast.containsKey(it.id)) "✓" else null })
                BigClock(endsAt)
            }
            UStage.ELIM -> {
                val out = s.players.firstOrNull { it.id == s.eliminated }
                if (out == null) Heading(null, "Empate: ninguém saiu")
                else { Avatar(out, 96.dp, ring = true); Heading(null, "${out.name} saiu"); Stamp(roleLabel(s.roles[out.id]).uppercase()) }
            }
            UStage.GUESS -> Heading("Última chance", "O Sr. Branco está chutando a palavra…")
            UStage.DONE -> Unit
        }
    }
}

// --- Frequência ----------------------------------------------------------------------------------

@Composable
private fun FrequencyStage(s: FrequencyState, endsAt: Long?) {
    Centered {
        val pair = s.pair
        Text(
            "Carta ${s.round + 1} de ${s.spectrums.size}  ·  " + if (s.coop) "${s.points[0]} pontos" else "Verde ${s.points[0]} × ${s.points[1]} Roxo",
            style = Stage.Body, color = Tv.Tertiary,
        )
        Dial(s)
        Row(Modifier.width(480.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pair[0], style = Stage.Title, color = Tv.Primary)
            Text(pair.getOrElse(1) { "" }, style = Stage.Title, color = Tv.Primary)
        }
        val who = s.players.first { it.id == s.psychic }.name
        when (s.stage) {
            FStage.CLUE -> Text("$who vê o alvo e dá uma pista em voz alta", style = Stage.Body, color = Tv.Secondary)
            FStage.DIAL -> Text("◀ ▶ gira o dial  ·  OK trava", style = Stage.Body, color = Tv.Secondary)
            FStage.BET -> Text("O outro time aposta: o alvo está à esquerda ou à direita?", style = Stage.Body, color = Tv.Secondary)
            FStage.REVEAL -> {
                Text(if (s.lastGain > 0) "+${s.lastGain} pontos" else "Passou longe", style = Stage.Hero, color = if (s.lastGain >= 3) Stage.Gold else Tv.Primary)
                s.betRight?.let { Text(if (it) "A aposta do outro time acertou: +1" else "A aposta errou", style = Stage.Body, color = Tv.Secondary) }
            }
            FStage.DONE -> Unit
        }
        if (s.stage == FStage.CLUE || s.stage == FStage.DIAL || s.stage == FStage.BET) BigClock(endsAt)
    }
}

/** A half-circle dial: the needle is the team's guess; the target and its bands show only on the reveal. */
@Composable
private fun Dial(s: FrequencyState) {
    val show = s.stage == FStage.REVEAL || s.stage == FStage.DONE
    Canvas(Modifier.size(480.dp, 250.dp)) {
        val r = size.width / 2f - 16f
        val center = Offset(size.width / 2f, size.height - 12f)
        val box = Size(r * 2, r * 2)
        val tl = Offset(center.x - r, center.y - r)
        fun angle(v: Int) = 180f + v * 1.8f
        drawArc(Color.White.copy(alpha = 0.12f), 180f, 180f, useCenter = true, topLeft = tl, size = box)
        if (show) {
            listOf(15 to 0.25f, 9 to 0.45f, 4 to 0.8f).forEach { (w, a) ->
                drawArc(Color(0xFF30D158).copy(alpha = a), angle((s.target - w).coerceAtLeast(0)), (minOf(100, s.target + w) - maxOf(0, s.target - w)) * 1.8f, useCenter = true, topLeft = tl, size = box)
            }
        }
        val a = Math.toRadians(angle(s.dial).toDouble())
        drawLine(Color.White, center, Offset(center.x + (r - 10f) * Math.cos(a).toFloat(), center.y + (r - 10f) * Math.sin(a).toFloat()), strokeWidth = 8f, cap = StrokeCap.Round)
        drawCircle(Color.White, 14f, center)
        drawArc(Color.White.copy(alpha = 0.5f), 180f, 180f, useCenter = false, topLeft = tl, size = box, style = Stroke(3f))
    }
}

// --- Sintonia de Casal --------------------------------------------------------------------------

@Composable
private fun CoupleStage(s: CoupleState, endsAt: Long?) {
    Centered {
        val pct = s.points * 100 / maxOf(1, s.maxPoints)
        Text("Pergunta ${s.round + 1} de ${s.questions.size}  ·  sintonia $pct%", style = Stage.Body, color = Tv.Tertiary)
        Text(s.text, style = Stage.Hero, color = Tv.Primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 800.dp), maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.Top) {
            s.players.forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Avatar(p, 64.dp, ring = p.id == s.subject.id)
                    Text(p.name, style = Stage.Body, color = Tv.Primary)
                    val a = s.answers[p.id]
                    Text(
                        when {
                            s.stage == CStage.ANSWER -> if (a != null) "travado ✓" else "pensando…"
                            a == null -> "—"
                            s.q.scale -> "$a / 10"
                            else -> s.q.options.getOrElse(a) { "?" }
                        },
                        style = Stage.Title, color = if (s.stage == CStage.ANSWER) Tv.Secondary else Tv.Primary,
                    )
                    if (p.id == s.subject.id) Text("responde sobre si", style = Stage.Body, color = Tv.Tertiary) else Text("tenta adivinhar", style = Stage.Body, color = Tv.Tertiary)
                }
            }
        }
        if (s.stage == CStage.REVEAL) Text(if (s.lastPoints > 0) "+${s.lastPoints} de sintonia" else "Dessa vez não bateu", style = Stage.Title, color = if (s.lastPoints > 0) Stage.Gold else Tv.Secondary)
        if (s.stage == CStage.ANSWER) { val (_, share) = rememberCountdown(endsAt); TimeBar(share.value, Modifier.width(420.dp)) }
    }
}

// --- Duas Verdades -----------------------------------------------------------------------------

@Composable
private fun TwoTruthsStage(s: TwoTruthsState, endsAt: Long?) {
    Centered {
        Avatar(s.teller, 72.dp, ring = true)
        when (s.stage) {
            TStage.PREP -> Heading("Tema: ${s.theme}", "${s.teller.name} tá pensando…", "Duas verdades e uma mentira")
            TStage.TELL -> Heading("Tema: ${s.theme}", "${s.teller.name} conta as três", "Prestem atenção")
            TStage.VOTE, TStage.REVEAL -> Text("${s.teller.name}  ·  tema: ${s.theme}", style = Stage.Title, color = Tv.Secondary)
            TStage.DONE -> Unit
        }
        if (s.stage == TStage.VOTE || s.stage == TStage.REVEAL) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                (0..2).forEach { i ->
                    val votes = s.votes.cast.values.count { it == i }
                    val isLie = s.stage == TStage.REVEAL && s.lie == i
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.size(130.dp, 150.dp).background(if (isLie) Color(0x33FF453A) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", style = Stage.Hero, color = Tv.Primary) }
                        if (s.stage == TStage.REVEAL) { if (isLie) Stamp("MENTIRA") else Text("verdade", style = Stage.Body, color = Tv.Secondary); Text("$votes voto${if (votes == 1) "" else "s"}", style = Stage.Body, color = Tv.Secondary) }
                    }
                }
            }
            if (s.stage == TStage.VOTE) { Text("${s.votes.cast.size} de ${s.votes.voters.size} votaram", style = Stage.Body, color = Tv.Secondary); BigClock(endsAt) }
            if (s.stage == TStage.REVEAL) Text("${s.fooled[s.teller.id] ?: 0} enganado${if ((s.fooled[s.teller.id] ?: 0) == 1) "" else "s"}", style = Stage.Title, color = Stage.Gold)
        }
        if (s.stage == TStage.PREP || s.stage == TStage.TELL) BigClock(endsAt)
    }
}

// --- Lugar Secreto ------------------------------------------------------------------------------

@Composable
private fun SpyfallStage(s: SpyState, endsAt: Long?) {
    Centered {
        when (s.stage) {
            SStage.ROLES -> { Heading(null, "Olha o seu papel", "Segura pra ver e toca em “Já vi”"); People(s.players, mark = { if (it.id in s.ready) "✓" else null }) }
            SStage.TALK -> { BigClock(endsAt, "Tempo"); Text("Façam perguntas uns pros outros. O espião tenta descobrir onde vocês estão", style = Stage.Body, color = Tv.Secondary) }
            SStage.PICK -> Heading(null, "${s.name(s.accuser)} vai acusar alguém…")
            SStage.VOTE_ACCUSATION -> { Heading(null, "${s.name(s.accuser)} acusa ${s.name(s.accused)}", "Todos precisam concordar"); People(s.players.filter { it.id in s.yes.voters }, mark = { if (s.yes.cast.containsKey(it.id)) "✓" else null }) }
            SStage.GUESS -> Heading(null, "O espião vai arriscar o local")
            SStage.FINAL -> { Heading("Acabou o tempo", "Quem é o espião?", "Votem pelo celular"); People(s.players, mark = { if (s.finalVotes.cast.containsKey(it.id)) "✓" else null }) }
            SStage.DONE -> Unit
        }
        if (s.stage != SStage.DONE && s.stage != SStage.ROLES) {
            Text("Possíveis locais", style = Stage.Body, color = Tv.Tertiary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 860.dp)) {
                s.places.forEach { Text(it, style = Tv.Headline, color = Tv.Secondary, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) }
            }
        }
    }
}

// --- Quem Sabia? -----------------------------------------------------------------------------

@Composable
private fun InsiderStage(s: InsiderState, endsAt: Long?) {
    Centered {
        when (s.stage) {
            IStage.ROLES -> { Heading(null, "Olha o seu papel", "O Mestre e o Infiltrado veem a palavra"); People(s.players, mark = { if (it.id in s.ready) "✓" else null }) }
            IStage.PLAY -> {
                Heading("Mestre: ${s.name(s.master)}", "Descubram a palavra", "Perguntas de sim ou não, em voz alta")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.history.takeLast(10).forEach { h ->
                        val (t, c) = when (h) { "yes" -> "SIM" to Color(0xFF30D158); "no" -> "NÃO" to Color(0xFFFF453A); else -> "?" to Color(0xFFFFD60A) }
                        Text(t, style = Tv.Headline, color = Color.Black, modifier = Modifier.background(c, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                Text("${s.history.size} respostas", style = Stage.Body, color = Tv.Tertiary)
                BigClock(endsAt)
            }
            IStage.VOTE -> {
                Heading("Acharam a palavra!", "Quem tava guiando vocês?", "Votem no Infiltrado")
                People(s.players.filter { it.id != s.master }, mark = { if (s.votes.cast.containsKey(it.id)) "✓" else null })
                BigClock(endsAt)
            }
            IStage.DONE -> Unit
        }
    }
}

// --- Psicólogo -------------------------------------------------------------------------------

@Composable
private fun PsychologistStage(s: PsychologistState, endsAt: Long?) {
    val psy = s.players.first { it.id == s.psychologist }
    Centered {
        Avatar(psy, 80.dp, ring = true)
        when (s.stage) {
            PStage.READ -> { Heading(null, "${psy.name} é o psicólogo", "Vira de costas enquanto o resto lê a regra"); People(s.players.filter { it.id != psy.id }, mark = { if (it.id in s.ready) "✓" else null }) }
            PStage.ASK -> { Heading(null, "${psy.name} faz perguntas", "Descubra a regra secreta da turma"); Text("♥".repeat(s.lives) + "♡".repeat(3 - s.lives) + "   palpites: ${s.guesses}", style = Stage.Title, color = Color(0xFFFF6482)); BigClock(endsAt) }
            PStage.JUDGE -> { Heading(null, "${psy.name} vai chutar a regra", "A sala julga: acertou?"); People(s.players.filter { it.id != psy.id }, mark = { if (s.judge.cast.containsKey(it.id)) "✓" else null }) }
            PStage.DONE -> Unit
        }
    }
}

// --- Mistério Sim ou Não --------------------------------------------------------------------

@Composable
private fun BlackStoryStage(s: BlackStoryState, endsAt: Long?) {
    Centered {
        if (s.stage == BStage.PLAY) {
            Text("Guardião: ${s.players.first { it.id == s.guardian }.name}", style = Stage.Body, color = Tv.Tertiary)
            Text(s.mystery.riddle, style = Stage.Title, color = Tv.Primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 800.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.history.takeLast(10).forEach { h ->
                    val (t, c) = when (h) { "yes" -> "SIM" to Color(0xFF30D158); "no" -> "NÃO" to Color(0xFFFF453A); "almost" -> "QUASE" to Color(0xFFFFD60A); else -> "IRRELEVANTE" to Color(0xFF98989D) }
                    Text(t, style = Tv.Headline, color = Color.Black, modifier = Modifier.background(c, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            s.mystery.hints.take(s.hintsShown).forEachIndexed { i, h -> Text("Dica ${i + 1}: $h", style = Stage.Body, color = Stage.Gold) }
            BigClock(endsAt)
        } else {
            Text(s.mystery.riddle, style = Stage.Body, color = Tv.Tertiary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 800.dp))
            Typewriter(s.mystery.solution)
            Text(if (s.solved) "Resolveram com ${s.hintsShown} dica${if (s.hintsShown == 1) "" else "s"}" else "Ninguém resolveu desta vez", style = Stage.Body, color = Tv.Secondary)
        }
    }
}

/** The solution writes itself, one character at a time (about 30 ms each), then stops: no idle redraw. */
@Composable
private fun Typewriter(text: String) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) { while (shown < text.length) { delay(30); shown++ } }
    Text(text.take(shown), style = Stage.Title, color = Tv.Primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 800.dp).height(150.dp))
}

// --- Missões (modo ambiente) ---------------------------------------------------------------

@Composable
private fun MissionsStage(s: MissionsState, endsAt: Long?) {
    // Ambient: the clock moves once a minute, nothing else animates while people talk
    Centered {
        Heading(null, "Missões secretas em andamento", "Conversem. Cumpram. Desconfiem.")
        val (left, _) = rememberCountdown(endsAt)
        Text("Faltam ${(left.value + 59) / 60} min", style = Stage.Title, color = Tv.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            s.players.sortedByDescending { s.points[it.id] ?: 0 }.forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Avatar(p, 44.dp); Text("${s.points[p.id] ?: 0}", style = Stage.Title, color = Stage.Gold) }
            }
        }
        s.feed.takeLast(4).reversed().forEach { f ->
            val who = s.players.firstOrNull { it.id == f.who }?.name ?: "?"
            val other = s.players.firstOrNull { it.id == f.other }?.name
            Text(
                when (f.kind) { "done" -> "$who cumpriu uma missão! +${f.n}"; "unmasked" -> "$who desmascarou $other!"; else -> "$who desconfiou de $other, mas errou" },
                style = Stage.Body, color = Tv.Secondary,
            )
        }
    }
}

// --- Máfia de Uma Noite ----------------------------------------------------------------------

@Composable
private fun MafiaStage(s: MafiaState, endsAt: Long?) {
    Centered {
        when (s.stage) {
            MStage.ROLES -> { Heading(null, "Olha o seu papel", "Segura pra ver e toca em “Já vi”"); People(s.players, mark = { if (it.id in s.ready) "✓" else null }) }
            MStage.NIGHT -> {
                val role = s.stepRole
                Text("A cidade dorme…", style = Stage.Body, color = Tv.Tertiary)
                Text(
                    when (role) {
                        dev.jean.atvhome.social.games.mafia.MRole.MAFIOSO -> "Mafiosos, abram os olhos e se reconheçam"
                        dev.jean.atvhome.social.games.mafia.MRole.INFORMANTE -> "Informante, descubra quem é da máfia"
                        dev.jean.atvhome.social.games.mafia.MRole.DETETIVE -> "Detetive, investigue uma carta"
                        dev.jean.atvhome.social.games.mafia.MRole.BATEDOR -> "Batedor, troque sua carta"
                        dev.jean.atvhome.social.games.mafia.MRole.FOFOQUEIRO -> "Fofoqueiro, troque duas cartas"
                        dev.jean.atvhome.social.games.mafia.MRole.SONAMBULO -> "Sonâmbulo, vá ao centro das cartas"
                        else -> ""
                    },
                    style = Stage.Hero, color = Tv.Primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 800.dp),
                )
                Text("Todo mundo toca no celular, tendo poder ou não", style = Stage.Body, color = Tv.Secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NIGHT_ORDER.indices.forEach { i -> Box(Modifier.size(if (i == s.step) 16.dp else 10.dp).background(if (i <= s.step) Color.White else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))) } }
            }
            MStage.DAY -> { Heading("Amanheceu", "Quem é o mafioso?", "Os papéis podem ter sido trocados. Conversem!"); BigClock(endsAt, "Debate") }
            MStage.VOTE -> { Heading(null, "Hora de votar", "Se um mafioso for eliminado, a cidade vence"); People(s.players, mark = { if (s.votes.cast.containsKey(it.id)) "✓" else null }); BigClock(endsAt) }
            MStage.DONE -> Unit
        }
    }
}

// --- Tribunal dos Amigos -------------------------------------------------------------------------

@Composable
private fun TribunalStage(s: TribunalState, endsAt: Long?) {
    Centered {
        when (s.stage) {
            TrStage.INTRO -> { Heading("O caso", s.crime, "Lê seu dossiê no celular. O culpado também sabe que é ele"); People(s.players, mark = { if (it.id in s.ready) "✓" else null }) }
            TrStage.PROOF -> {
                Text(s.crime, style = Stage.Body, color = Tv.Tertiary)
                s.proofs().forEachIndexed { i, p -> Text("Prova ${i + 1}: $p", style = Stage.Title, color = if (i == s.proof) Stage.Gold else Tv.Secondary, textAlign = TextAlign.Center) }
                Text(s.suspects().size.let { if (it == 1) "Só 1 suspeito se encaixa" else "Ainda cabem $it suspeitos" }, style = Stage.Body, color = Tv.Secondary)
                s.lastObjection?.let { o -> LaunchedEffect(s.objections.values.sum()) { dev.jean.atvhome.social.Sfx.play(dev.jean.atvhome.social.Sfx.Effect.OBJECTION) }; Stamp("OBJEÇÃO!", Color(0xFFFF9F0A)); Text("${s.name(o)} tem a palavra", style = Stage.Body, color = Tv.Primary) }
                BigClock(endsAt, "Debate")
            }
            TrStage.VERDICT -> { Heading("Veredito", "Quem é o culpado?", s.proofs().joinToString("  ·  ")); People(s.players, mark = { if (s.votes.cast.containsKey(it.id)) "✓" else null }); BigClock(endsAt) }
            TrStage.DONE -> Unit
        }
    }
}

// --- Filme em 5 Pistas ----------------------------------------------------------------------

/** The poster starts as a handful of big squares and sharpens with every clue: decoded small, scaled up unsmoothed. */
@Composable
private fun PixelPoster(url: String, step: Int, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (w, h) = listOf(8 to 12, 16 to 24, 32 to 48, 64 to 96, 200 to 300).getOrElse(step) { 200 to 300 }
    val request = remember(url, step) {
        coil3.request.ImageRequest.Builder(context).data(url).size(w, h).precision(coil3.size.Precision.EXACT).crossfade(false).build()
    }
    coil3.compose.AsyncImage(
        request, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        modifier = modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
    )
}

@Composable
private fun FilmStage(s: FilmState, endsAt: Long?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        val sharp = if (s.stage == FiStage.CLUE) s.clue else 4
        PixelPoster(s.film.poster, sharp, Modifier.size(200.dp, 300.dp))
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filme ${s.round + 1} de ${s.films.size}" + if (s.stage == FiStage.CLUE) "  ·  vale ${5 - s.clue} ponto${if (5 - s.clue == 1) "" else "s"} agora" else "", style = Stage.Body, color = Tv.Tertiary)
            if (s.stage == FiStage.REVEAL) {
                Text(s.film.title, style = Stage.Hero, color = Tv.Primary, maxLines = 2)
                val got = s.players.filter { p -> s.answers[p.id]?.second == s.rightIndex }
                Text(if (got.isEmpty()) "Ninguém acertou" else got.joinToString { it.name } + " acertou" + if (got.size > 1) "aram" else "", style = Stage.Title, color = Stage.Gold)
                if (s.film.where.isNotBlank()) Text("Disponível no ${s.film.where}", style = Stage.Body, color = Tv.Secondary)
            } else {
                s.clues().forEachIndexed { i, c -> Text("${i + 1}.  $c", style = if (i == s.clue) Stage.Title else Stage.Body, color = if (i == s.clue) Tv.Primary else Tv.Secondary) }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.opts.forEachIndexed { i, t -> Text("${'A' + i}  $t", style = Tv.Headline, color = Tv.Primary, maxLines = 1, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) }
                }
                People(s.players, mark = { if (s.answers.containsKey(it.id)) "✓" else null })
                BigClock(endsAt)
            }
        }
    }
}

// --- Desarme a Bomba -----------------------------------------------------------------------------

private fun wireColor(name: String) = when (name) {
    "vermelho" -> Color(0xFFFF453A); "azul" -> Color(0xFF0A84FF); "amarelo" -> Color(0xFFFFD60A); "branco" -> Color(0xFFF2F2F7); else -> Color(0xFF3A3A3C)
}

@Composable
private fun BombStageView(s: BombState, endsAt: Long?) {
    val (left, share) = rememberCountdown(endsAt)
    // A short red flash on every strike: one pre-drawn layer fading, no per-frame work otherwise
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(s.strikes) { if (s.strikes > 0) { dev.jean.atvhome.social.Sfx.play(dev.jean.atvhome.social.Sfx.Effect.STRIKE); flash.snapTo(0.55f); flash.animateTo(0f, tween(450)) } }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                val sec = left.value
                Text("%d:%02d".format(sec / 60, sec % 60), style = Stage.Hero.copy(fontSize = 64.sp, lineHeight = 68.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = if (sec <= 30) Color(0xFFFF453A) else Tv.Primary)
                Text((1..3).joinToString(" ") { if (it <= s.strikes) "✕" else "○" }, style = Stage.Hero, color = Color(0xFFFF453A))
            }
            TimeBar(share.value, Modifier.width(520.dp))
            val f = s.focus
            if (f == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.modules.forEachIndexed { i, m ->
                        Box(
                            Modifier.size(120.dp, 86.dp).background(if (m.solved) Color(0x3330D158) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                .then(if (i == s.sel) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (m.solved) "✓ ${m.title}" else m.title, style = Tv.Headline, color = if (m.solved) Color(0xFF30D158) else Tv.Primary) }
                    }
                }
                Text("◀ ▶ escolhe o módulo  ·  OK abre", style = Stage.Body, color = Tv.Tertiary)
            } else ModuleView(s, s.modules[f])
            val e = s.edge
            Text("Série ${e.serial}  ·  ${e.batteries} bateria${if (e.batteries == 1) "" else "s"}  ·  CAR ${if (e.car) "●" else "○"}  FRK ${if (e.frk) "●" else "○"}", style = Stage.Body, color = Tv.Secondary)
            Text("${s.solvedCount} de ${s.modules.size} módulos  ·  ${s.players.first { it.id == s.defuser }.name} desarma; os outros leem o manual", style = Tv.Meta, color = Tv.Tertiary)
        }
        if (flash.value > 0.01f) Box(Modifier.matchParentSize().background(Color(0xFFFF0000).copy(alpha = flash.value)))
    }
}

@Composable
private fun ModuleView(s: BombState, m: dev.jean.atvhome.social.games.bomb.Module) {
    when (m) {
        is Wires -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            m.wires.forEachIndexed { i, c ->
                val cut = i in m.cut
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (i == s.cursor) "▶" else " ", style = Stage.Title, color = Tv.Primary, modifier = Modifier.width(30.dp))
                    Box(Modifier.size(if (cut) 150.dp else 320.dp, 14.dp).background(wireColor(c), RoundedCornerShape(7.dp)))
                    if (cut) Box(Modifier.size(150.dp, 14.dp).background(wireColor(c).copy(alpha = 0.35f), RoundedCornerShape(7.dp)))
                }
            }
            Text("▲ ▼ escolhe o fio  ·  OK corta", style = Stage.Body, color = Tv.Tertiary)
        }
        is PushButton -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(150.dp).background(wireColor(m.color), CircleShape), contentAlignment = Alignment.Center) {
                Text(m.word, style = Tv.Headline, color = if (m.color == "branco" || m.color == "amarelo") Color.Black else Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Faixa:", style = Stage.Body, color = Tv.Secondary)
                Box(Modifier.size(90.dp, 16.dp).background(if (m.held) wireColor(ButtonRules.stripOf(m)) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)))
            }
            Text(if (m.held) "Segurando… OK solta" else "OK aperta e segura", style = Stage.Body, color = Tv.Tertiary)
        }
        is Symbols -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (0..1).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (0..1).forEach { c ->
                        val i = r * 2 + c; val g = m.shown[i]
                        Box(
                            Modifier.size(96.dp).background(if (g in m.pressed) Color(0x4430D158) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                .then(if (i == s.cursor) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Text(SymbolTable.glyphs[g], style = Stage.Hero, color = Tv.Primary) }
                    }
                }
            }
            Text("◀ ▲ ▼ ▶ escolhe  ·  OK aperta", style = Stage.Body, color = Tv.Tertiary)
        }
        is Maze -> {
            val (m1, m2) = Mazes.markers(m.id)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until Mazes.SIZE).forEach { y ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            (0 until Mazes.SIZE).forEach { x ->
                                val c = y * Mazes.SIZE + x
                                Box(Modifier.size(38.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                    when {
                                        c == m.pos -> Box(Modifier.size(22.dp).background(Color.White, CircleShape))
                                        c == m.end -> Text("▣", style = Tv.Headline, color = Color(0xFF30D158))
                                        c == m1 || c == m2 -> Text("●", style = Tv.Headline, color = Tv.Secondary)
                                    }
                                }
                            }
                        }
                    }
                }
                Text("As paredes não aparecem: ◀ ▲ ▼ ▶ move; bater numa parede é um erro", style = Stage.Body, color = Tv.Tertiary)
            }
        }
        else -> Unit
    }
}

package dev.jean.atvhome.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.data.AdShield
import dev.jean.atvhome.guard.GuardSettings
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.components.requestWhenReady
import dev.jean.atvhome.ui.discover.Header
import dev.jean.atvhome.ui.theme.LocalAccent
import dev.jean.atvhome.ui.theme.Tv
import dev.jean.atvhome.ui.theme.screen
import kotlinx.coroutines.launch

/**
 * Everything "Sem anúncios" does, each one the person's choice, from the Central. Changes take
 * effect from the next video: the guard reads them each time an app starts playing.
 * [askCapture]: the screen reader was just switched on (the caller asks for the capture token).
 */
@Composable
fun AdShieldScreen(toast: (String) -> Unit, askCapture: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowed = remember { AdShield.allowed(context) }
    var on by remember { mutableStateOf(AdShield.isOn(context)) }
    var settings by remember { mutableStateOf(AdShield.settings(context)) }
    var dns by remember { mutableStateOf(AdShield.dnsOn(context)) }
    // Switching the network filter on checks that the TV still reaches the internet: a few seconds
    var checkingDns by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    // The list's first row exists only after the first layout: asked at once, the focus went nowhere
    // and the arrows moved nothing
    LaunchedEffect(Unit) { first.requestWhenReady() }

    fun change(next: GuardSettings) {
        val before = settings
        settings = next
        scope.launch {
            if (!AdShield.saveSettings(context, next)) { settings = before; toast("Não consegui salvar") }
            else if (on && next.readScreen && !before.readScreen) askCapture()
        }
    }

    fun yesNo(value: Boolean) = if (value) "sim" else "não"

    Column(Modifier.fillMaxSize().background(LocalAccent.current.screen())) {
        Header("Sem anúncios", "Nos apps oficiais, em qualquer streaming. As mudanças valem a partir do próximo vídeo.")
        if (!allowed) {
            Text(
                "Falta liberar uma vez pelo computador: tools/ad-guard-setup.sh",
                style = Tv.Body, color = Tv.Secondary, modifier = Modifier.padding(start = Tv.Edge, top = 10.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = Tv.Edge, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Option(
                    "Sem anúncios", if (on) "ligado" else "desligado",
                    "Desligado, nada desta tela age: os apps ficam exatamente como sem o Início.",
                    selected = on, modifier = Modifier.focusRequester(first),
                ) {
                    if (!allowed) { toast(NOT_ALLOWED); return@Option }
                    val next = !on
                    scope.launch {
                        if (!AdShield.set(context, next)) toast("Esta TV não deixou mudar as configurações")
                        else { on = next; if (next) askCapture() }
                    }
                }
            }
            item {
                Option("Pular anúncio", yesNo(settings.skip), "Aperta o botão \"Pular\" quando o app mostra um.") {
                    change(settings.copy(skip = !settings.skip))
                }
            }
            item {
                Option(
                    "Pular também pelo que a tela mostra", yesNo(settings.tapSkip),
                    "Toca onde aparece \"Pular\" quando o app não expõe o botão. Em teste: pode tocar no lugar errado.",
                ) { change(settings.copy(tapSkip = !settings.tapSkip)) }
            }
            item {
                Option(
                    "Anúncio que não dá para pular", if (settings.mute) "sem som" else "com som",
                    "Sem som, o anúncio aparece mudo e o som volta sozinho quando o filme volta.",
                ) { change(settings.copy(mute = !settings.mute)) }
            }
            item {
                Option(
                    "Ler a tela", yesNo(settings.readScreen),
                    "Para apps que só mostram o anúncio na imagem (Netflix, YouTube, Prime Video). Usa um pouco mais da TV.",
                ) { change(settings.copy(readScreen = !settings.readScreen)) }
            }
            item {
                Option(
                    "Contagem regressiva em qualquer idioma", yesNo(settings.countdown),
                    "Um número caindo no canto conta como anúncio, só nos apps em que já houve um anúncio de verdade.",
                ) { change(settings.copy(countdown = !settings.countdown)) }
            }
            item {
                val value = when { checkingDns -> "conferindo…"; dns -> "ligado"; else -> "desligado" }
                Option(
                    "Filtro na rede", value,
                    "Some com anúncios que vêm de servidor separado. Pode atrapalhar algum app ou um nome da rede de casa; nunca deixa a TV sem internet.",
                    selected = dns,
                ) {
                    if (checkingDns) return@Option
                    if (!allowed) { toast(NOT_ALLOWED); return@Option }
                    val next = !dns
                    checkingDns = true
                    scope.launch {
                        when (AdShield.setDns(context, next)) {
                            AdShield.Outcome.DONE -> dns = next
                            AdShield.Outcome.NO_ANSWER -> toast("O filtro não respondeu: a TV continua com a rede normal")
                            AdShield.Outcome.REFUSED -> toast("Esta TV não deixou mudar as configurações")
                        }
                        checkingDns = false
                    }
                }
            }
            item {
                Option("Recomeçar o aprendizado", null, "Esquece qual jeito de enxergar o anúncio funciona em cada app.") {
                    AdShield.resetLearning(context)
                    toast("A guarda vai aprender cada app de novo")
                }
            }
            item {
                Option("Voltar ao padrão", null, "Pular sim, com som, ler a tela e contagem regressiva ligados.") {
                    change(GuardSettings())
                }
            }
        }
    }
}

/** One choice: the pill says what it is and how it is set; the line below says what it means. */
@Composable
private fun Option(
    title: String,
    value: String?,
    help: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column {
        PillButton(if (value == null) title else "$title: $value", onClick, modifier, selected = selected)
        Text(help, style = Tv.Meta, color = Tv.Tertiary, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
    }
}

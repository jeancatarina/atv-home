package dev.jean.atvhome.ui.panel

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.data.AdShield
import dev.jean.atvhome.data.TvSystem
import kotlinx.coroutines.launch
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.theme.Tv

/**
 * tvOS's Control Center, on the MENU key: TV inputs and the settings people reach for.
 * [onAdShieldOn]: "Sem anúncios" was just switched on (the caller asks for the screen-capture token).
 * [onAdSettings]: opens the "Sem anúncios" screen, with every choice.
 */
@Composable
fun QuickPanel(onClose: () -> Unit, toast: (String) -> Unit, onAdShieldOn: () -> Unit = {}, onAdSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val inputs = remember { TvSystem.inputs(context) }
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var shield by remember { mutableStateOf(AdShield.isOn(context)) }
    val home = (context.applicationContext as dev.jean.atvhome.App).home
    val row = home?.row?.collectAsState()?.value.orEmpty()
    val lit = home?.states?.collectAsState()?.value.orEmpty()
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp).padding(18.dp)
                .background(Color(0xF21C1C1E), RoundedCornerShape(22.dp)).padding(18.dp),
        ) {
            Text("Central", style = Tv.Title, color = Tv.Primary)
            LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inputs.isNotEmpty()) {
                    item { Text("Entradas", style = Tv.Meta, color = Tv.Secondary) }
                    items(inputs, key = { it.id }) { input ->
                        PillButton(input.label, {
                            onClose()
                            if (!TvSystem.switchTo(context, input)) toast("Não consegui mudar para ${input.label}")
                        }, modifier = Modifier.fillMaxWidth().let { if (input == inputs.first()) it.focusRequester(first) else it })
                    }
                }
                if (home != null) {
                    item { Text("Casa", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(top = 6.dp)) }
                    // The first lights of the Casa row, with their state in the label: the Central opens over any screen
                    items(row.take(3), key = { "casa:" + it.key }) { light ->
                        val on = dev.jean.atvhome.tuya.Panel.isOn(light, lit)
                        PillButton("${light.name}: ${if (on) "acesa" else "apagada"}", {
                            scope.launch { if (!home.toggle(light)) toast("Não consegui falar com o hub") }
                        }, modifier = Modifier.fillMaxWidth(), selected = on)
                    }
                    item {
                        PillButton("Apagar tudo", {
                            scope.launch { if (!home.allOff()) toast("Algumas luzes não responderam") }
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
                item { Text("Sistema", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(top = 6.dp)) }
                item {
                    PillButton("Protetor de tela agora", {
                        onClose(); if (!TvSystem.startScreensaver(context)) toast("Esta TV não deixa iniciar o protetor de tela")
                    }, modifier = Modifier.fillMaxWidth().let { if (inputs.isEmpty()) it.focusRequester(first) else it })
                }
                // Stays open on purpose: the label is the feedback
                item {
                    PillButton(if (shield) "Sem anúncios: ligado" else "Sem anúncios: desligado", {
                        if (!AdShield.allowed(context)) { toast(NOT_ALLOWED); return@PillButton }
                        val next = !shield
                        scope.launch {
                            if (!AdShield.set(context, next)) toast("Esta TV não deixou mudar as configurações")
                            else { shield = next; if (next) onAdShieldOn() }
                        }
                    }, modifier = Modifier.fillMaxWidth(), selected = shield)
                }
                item { PillButton("Ajustes do Sem anúncios", { onClose(); onAdSettings() }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Wi-Fi", { onClose(); TvSystem.openSettings(context, Settings.ACTION_WIFI_SETTINGS) }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Imagem e som", { onClose(); TvSystem.openSettings(context, Settings.ACTION_DISPLAY_SETTINGS) }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Configurações", { onClose(); TvSystem.openSettings(context) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

internal const val NOT_ALLOWED = "Falta liberar pelo computador: tools/ad-guard-setup.sh"

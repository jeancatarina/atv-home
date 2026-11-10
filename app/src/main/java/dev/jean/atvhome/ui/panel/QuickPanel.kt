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
 */
@Composable
fun QuickPanel(onClose: () -> Unit, toast: (String) -> Unit, onAdShieldOn: () -> Unit = {}) {
    val context = LocalContext.current
    val inputs = remember { TvSystem.inputs(context) }
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var shield by remember { mutableStateOf(AdShield.isOn(context)) }
    var dns by remember { mutableStateOf(AdShield.dnsOn(context)) }
    // Switching the network filter on checks that the TV still reaches the internet: a few seconds
    var checkingDns by remember { mutableStateOf(false) }
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
                item { Text("Sistema", style = Tv.Meta, color = Tv.Secondary, modifier = Modifier.padding(top = 6.dp)) }
                item {
                    PillButton("Protetor de tela agora", {
                        onClose(); if (!TvSystem.startScreensaver(context)) toast("Esta TV não deixa iniciar o protetor de tela")
                    }, modifier = Modifier.fillMaxWidth().let { if (inputs.isEmpty()) it.focusRequester(first) else it })
                }
                // Both stay open on purpose: the label is the feedback
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
                item {
                    val label = when {
                        checkingDns -> "Filtro na rede: conferindo…"
                        dns -> "Filtro na rede: ligado"
                        else -> "Filtro na rede: desligado"
                    }
                    PillButton(label, {
                        if (checkingDns) return@PillButton
                        if (!AdShield.allowed(context)) { toast(NOT_ALLOWED); return@PillButton }
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
                    }, modifier = Modifier.fillMaxWidth(), selected = dns)
                }
                item { PillButton("Wi-Fi", { onClose(); TvSystem.openSettings(context, Settings.ACTION_WIFI_SETTINGS) }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Imagem e som", { onClose(); TvSystem.openSettings(context, Settings.ACTION_DISPLAY_SETTINGS) }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Configurações", { onClose(); TvSystem.openSettings(context) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

private const val NOT_ALLOWED = "Falta liberar pelo computador: tools/ad-guard-setup.sh"

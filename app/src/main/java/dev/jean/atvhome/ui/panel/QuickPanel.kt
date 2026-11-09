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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.data.TvSystem
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.theme.Tv

/** tvOS's Control Center, on the MENU key: TV inputs and the settings people reach for. */
@Composable
fun QuickPanel(onClose: () -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current
    val inputs = remember { TvSystem.inputs(context) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp).padding(18.dp)
                .background(Color(0xF21C1C1E), RoundedCornerShape(22.dp)).padding(18.dp),
        ) {
            Text("Central", style = Tv.Title, color = Tv.Primary)
            Text("MENU ou VOLTAR fecha", style = Tv.Caption, color = Tv.Tertiary)
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
                item { PillButton("Wi-Fi", { onClose(); TvSystem.openSettings(context, Settings.ACTION_WIFI_SETTINGS) }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Imagem e som", { onClose(); TvSystem.openSettings(context, Settings.ACTION_DISPLAY_SETTINGS) }, modifier = Modifier.fillMaxWidth()) }
                item { PillButton("Configurações", { onClose(); TvSystem.openSettings(context) }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

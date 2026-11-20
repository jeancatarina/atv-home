package dev.jean.atvhome.ui.panel

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.jean.atvhome.data.AdShield
import dev.jean.atvhome.data.TvSystem
import dev.jean.atvhome.saver.Weather
import dev.jean.atvhome.saver.WeatherLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.jean.atvhome.ui.components.PillButton
import dev.jean.atvhome.ui.theme.Tv

/**
 * tvOS's Control Center, on the MENU key and Home's Central button: TV inputs and the settings people reach for.
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
    var pickingCity by remember { mutableStateOf(false) }
    var cityLabel by remember { mutableStateOf(Weather.currentCity(context)) }
    val home = (context.applicationContext as dev.jean.atvhome.App).home
    val row = home?.row?.collectAsState()?.value.orEmpty()
    val lit = home?.states?.collectAsState()?.value.orEmpty()
    LaunchedEffect(pickingCity) { if (!pickingCity) runCatching { first.requestFocus() } }
    BackHandler(enabled = pickingCity) { pickingCity = false }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp).padding(18.dp)
                .background(Color(0xF21C1C1E), RoundedCornerShape(22.dp)).padding(18.dp),
        ) {
            if (pickingCity) {
                CityPicker(
                    saved = Weather.savedLocation(context),
                    onPick = { loc ->
                        Weather.saveLocation(context, loc)
                        cityLabel = Weather.currentCity(context)
                        pickingCity = false
                        toast(if (loc == null) "Cidade do clima: automática (por IP)" else "Clima definido para ${loc.city}")
                    },
                    onBack = { pickingCity = false },
                )
            } else {
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
                    item {
                        PillButton("Clima: ${cityLabel ?: "Automático"}", { pickingCity = true }, modifier = Modifier.fillMaxWidth())
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
}

@Composable
private fun CityPicker(
    saved: WeatherLocation?,
    onPick: (WeatherLocation?) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherLocation>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        results = Weather.searchCity(query)
    }
    Text("Cidade do clima", style = Tv.Title, color = Tv.Primary)
    LazyColumn(contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PillButton("Voltar", onBack, modifier = Modifier.fillMaxWidth().focusRequester(first))
        }
        item {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = Tv.Body.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(
                        Modifier.background(if (focused) Color.White.copy(alpha = 0.22f) else Tv.Pill, RoundedCornerShape(14.dp))
                            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = if (focused) 0.9f else 0f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (query.isEmpty()) Text("Buscar cidade…", style = Tv.Body, color = Tv.Tertiary)
                        inner()
                    }
                },
            )
        }
        item {
            PillButton(
                "Automático (pelo IP)",
                { onPick(null) },
                modifier = Modifier.fillMaxWidth(),
                selected = saved == null && Weather.configuredLocation() == null,
            )
        }
        val list = if (results.isNotEmpty()) results else PRESET_CITIES
        items(list, key = { "${it.city}:${it.lat}:${it.lon}" }) { loc ->
            val label = if (loc.detail.isNotBlank()) "${loc.city} · ${loc.detail}" else loc.city
            PillButton(label, { onPick(loc) }, modifier = Modifier.fillMaxWidth(), selected = saved?.city == loc.city)
        }
    }
}

private val PRESET_CITIES = listOf(
    WeatherLocation("Joinville", -26.30444, -48.84556, "SC, BR"),
    WeatherLocation("Florianópolis", -27.59667, -48.54917, "SC, BR"),
    WeatherLocation("Itajaí", -26.90778, -48.66194, "SC, BR"),
    WeatherLocation("Curitiba", -25.42778, -49.27306, "PR, BR"),
    WeatherLocation("São Paulo", -23.54750, -46.63611, "SP, BR"),
)

internal const val NOT_ALLOWED = "Falta liberar pelo computador: tools/ad-guard-setup.sh"

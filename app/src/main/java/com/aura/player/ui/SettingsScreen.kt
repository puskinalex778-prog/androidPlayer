package com.aura.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.player.playback.AudioEngine

@Composable
fun SettingsScreen(vm: MainViewModel, bottomPadding: Int) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    var sleepDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottomPadding.dp)) {
        item {
            Text("Настройки", style = MaterialTheme.typography.displaySmall, color = c.text, modifier = Modifier.padding(top = 8.dp))
        }

        // ---- темы ----
        item { SectionTitle("🎨 Тема оформления") }
        item {
            val themes = AuraTheme.entries.filter { it != AuraTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                themes.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t -> ThemeTile(t, s.theme == t, Modifier.weight(1f)) { vm.setTheme(t) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Режим", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Как в системе", s.darkMode == 0, { vm.setDarkMode(0) }, leading = "📱")
                        Pill("Тёмный", s.darkMode == 1, { vm.setDarkMode(1) }, leading = "🌙")
                        Pill("Светлый", s.darkMode == 2, { vm.setDarkMode(2) }, leading = "☀️")
                    }
                    ToggleRow("Чёрный AMOLED-фон", "Экономит заряд на OLED-экранах", s.amoled) { vm.setAmoled(it) }
                    ToggleRow("Цвет из обложки", "Интерфейс подстраивается под обложку песни", s.adaptive) { vm.setAdaptive(it) }
                    ToggleRow("Вид кассеты в плеере", "Ретро-кассета с обложкой на наклейке вместо большой обложки", s.cassette) { vm.setCassette(it) }
                }
            }
        }

        // ---- воспроизведение ----
        item { SectionTitle("▶️ Воспроизведение") }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingRow("Таймер сна", if (sleepAt > 0) "Включён" else "Выключен") { sleepDialog = true }
                    SettingRow("Работа в фоне", "Если музыка останавливается при выключенном экране — снимите для приложения ограничения батареи") {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    SettingRow("Системный звук", "Громкость и аудиоэффекты Android") {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
        }

        // ---- помощь ----
        item { SectionTitle("💾 Карта памяти") }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Как добавить песни с карты памяти", style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(
                        "1. Разрешите доступ к музыке — плеер сам найдёт песни во внутренней памяти И на карте памяти.\n" +
                            "2. Если какие-то файлы не появились — «Медиатека» → «Добавить папку» → в меню ☰ выберите карту памяти (SD) → нужную папку → «Использовать эту папку».\n" +
                            "3. Можно открыть песню прямо из файлового менеджера: «Открыть с помощью» → Aura Player.\n" +
                            "Поддерживаются MP3, FLAC, M4A/AAC, OGG/Opus, WAV, AMR и другие.",
                        style = MaterialTheme.typography.bodySmall, color = c.textDim,
                    )
                }
            }
        }
        item {
            Text(
                "Aura Player 1.0 · 2026\nKotlin · Jetpack Compose · Media3",
                style = MaterialTheme.typography.bodySmall, color = c.textDim, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }
    if (sleepDialog) SleepDialog(sleepAt) { sleepDialog = false }
}

@Composable
private fun ThemeTile(t: AuraTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape).background(c.glass).border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.glassBorder, shape)
            .clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(14.dp))
                .background(
                    if (t == AuraTheme.DYNAMIC) Brush.sweepGradient(listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEF4444)))
                    else Brush.linearGradient(listOf(t.primary, t.secondary, t.tertiary))
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAura.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!value) }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun SettingRow(title: String, sub: String, onClick: () -> Unit) {
    val c = LocalAura.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim)
    }
    Spacer(Modifier.height(0.dp))
}

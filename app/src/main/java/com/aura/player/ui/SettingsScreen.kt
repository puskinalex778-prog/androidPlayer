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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.player.playback.AudioEngine

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    section: Int,
    onSectionChange: (Int) -> Unit,
    onMenu: () -> Unit,
    bottomPadding: Int,
) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    var sleepDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        FlatTopBar(
            title = if (section == 0) "Темы и вид" else "Настройки",
            subtitle = if (section == 0) "Тема: ${s.theme.title}" else "Плеер и воспроизведение",
            onMenu = onMenu,
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            FlatSegmentedControl(
                options = listOf("Темы и вид", "Общие"),
                selectedIndex = section,
                onSelect = onSectionChange,
                icons = listOf(Icons.Rounded.Palette, Icons.Rounded.Tune),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding.dp),
        ) {
            if (section == 0) {
                // ===================== ТЕМЫ И ВИД =====================
                item {
                    SectionHeader("Режим яркости")
                    FlatSegmentedControl(
                        options = listOf("Система", "Тёмная", "Светлая"),
                        selectedIndex = s.darkMode,
                        onSelect = { vm.setDarkMode(it) },
                    )
                }
                item {
                    SectionHeader("Цветовая схема")
                    val themes = AuraTheme.entries.filter { it != AuraTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        themes.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { t ->
                                    ThemePreviewCard(t, s.theme == t, Modifier.weight(1f)) { vm.setTheme(t) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    SectionHeader("Интерфейс")
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            ToggleRow("Чёрный AMOLED-фон", "Чисто-чёрный фон в тёмном режиме", s.amoled) { vm.setAmoled(it) }
                            HorizontalDivider(color = c.border)
                            ToggleRow("Акцент из обложки", "Подстраивать цвет кнопок под обложку трека", s.adaptive) { vm.setAdaptive(it) }
                            HorizontalDivider(color = c.border)
                            ToggleRow("Ретро-кассета в плеере", "Кассета с обложкой альбома на наклейке", s.cassette) { vm.setCassette(it) }
                        }
                    }
                }
            } else {
                // ===================== ОБЩИЕ =====================
                item {
                    SectionHeader("Воспроизведение")
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            SettingRow(
                                icon = Icons.Rounded.Bedtime,
                                title = "Таймер сна",
                                sub = if (sleepAt > 0) "Активен · плавное выключение" else "Выключен",
                            ) { sleepDialog = true }
                            HorizontalDivider(color = c.border)
                            SettingRow(
                                icon = Icons.Rounded.BatteryChargingFull,
                                title = "Работа в фоне",
                                sub = "Снимите ограничения батареи, если музыка затихает при выключенном экране",
                            ) {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            HorizontalDivider(color = c.border)
                            SettingRow(
                                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                title = "Системный звук",
                                sub = "Громкость и параметры аудио Android",
                            ) {
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                    }
                }
                item {
                    SectionHeader("Медиатека")
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        SettingRow(
                            icon = Icons.Rounded.DeleteSweep,
                            title = "Очистить список музыки",
                            sub = if (tracks.isEmpty()) "Список уже пуст" else "Убрать все ${tracks.size} треков из плеера (файлы не удаляются)",
                            danger = tracks.isNotEmpty(),
                        ) { if (tracks.isNotEmpty()) confirmClear = true }
                    }
                }
                item {
                    SectionHeader("Карта памяти и файлы")
                    FlatCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.SdCard, null, tint = c.accent)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Как добавлять песни с SD-карты", style = MaterialTheme.typography.titleSmall, color = c.text)
                                Text(
                                    "1. Нажмите «Добавить папку» в разделе «Моя музыка».\n" +
                                        "2. В системном окне откройте меню ☰ слева сверху и выберите карту памяти.\n" +
                                        "3. Откройте нужную папку и нажмите «Использовать эту папку».\n" +
                                        "4. Или используйте «Открыть с помощью → Aura Player» из любого проводника.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textDim,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Aura Player 1.1 · Flat Edition 2026\nKotlin · Jetpack Compose · Media3",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        }
    }

    if (sleepDialog) SleepDialog(sleepAt) { sleepDialog = false }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.surface,
            title = { Text("Очистить список?", color = c.text) },
            text = { Text("Все добавленные треки будут убраны из плеера. Файлы на телефоне останутся нетронутыми.", color = c.textDim) },
            confirmButton = {
                TextButton(onClick = { vm.clearLibrary(); confirmClear = false }) { Text("Очистить", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ThemePreviewCard(t: AuraTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(14.dp)
    val previewAccent = if (t == AuraTheme.MONO) (if (c.dark) Color(0xFFF4F5F8) else Color(0xFF14161D)) else t.accent
    val previewBg = if (c.dark) Color(0xFF0D0E12) else Color(0xFFF3F4F7)
    val previewCard = if (c.dark) Color(0xFF1B1E27) else Color(0xFFFFFFFF)

    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // мини-превью плоского интерфейса темы
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(previewBg)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(previewAccent))
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(previewCard))
                if (selected) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(previewAccent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = if (t == AuraTheme.MONO && c.dark) Color.Black else Color.White, modifier = Modifier.size(11.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(0.7f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(previewAccent.copy(alpha = 0.65f)))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(previewCard))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(previewAccent.copy(alpha = 0.25f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            t.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) c.accent else c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAura.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalAura.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (danger) c.danger.copy(alpha = 0.14f) else c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (danger) c.danger else c.accent, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) c.danger else c.text)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textDim)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textDim, modifier = Modifier.size(20.dp))
    }
}

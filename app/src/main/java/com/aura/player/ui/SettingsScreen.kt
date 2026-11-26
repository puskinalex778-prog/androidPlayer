package com.aura.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.player.data.CustomTheme
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
    val customThemes by vm.customThemes.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    var sleepDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<CustomTheme?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val activeCustom = remember(customThemes, s.activeCustomThemeId) {
        customThemes.firstOrNull { it.id == s.activeCustomThemeId }
    }

    Column(Modifier.fillMaxSize()) {
        FlatTopBar(
            title = if (section == 0) "Темы и вид" else "Настройки",
            subtitle = if (section == 0) "${s.style.title} · ${activeCustom?.name ?: s.theme.title}" else "Плеер и воспроизведение",
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
                // ===================== СТИЛЬ ОФОРМЛЕНИЯ =====================
                item {
                    SectionHeader("Стиль оформления")
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UiStyle.entries.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { st ->
                                    StylePreviewCard(st, s.style == st, Modifier.weight(1f)) { vm.setUiStyle(st) }
                                }
                            }
                        }
                    }
                }
                // ===================== РЕЖИМ ЯРКОСТИ =====================
                item {
                    SectionHeader("Режим яркости")
                    FlatSegmentedControl(
                        options = listOf("Система", "Тёмная", "Светлая"),
                        selectedIndex = s.darkMode,
                        onSelect = { vm.setDarkMode(it) },
                    )
                }
                // ===================== ПОЛЬЗОВАТЕЛЬСКИЕ ТЕМЫ =====================
                item {
                    SectionHeader("Пользовательские темы") {
                        TextButton(onClick = { editingTheme = null; showEditor = true }) {
                            Text("+ Создать", color = c.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (customThemes.isEmpty()) {
                        FlatCard(
                            Modifier.fillMaxWidth(),
                            onClick = { editingTheme = null; showEditor = true },
                            padding = PaddingValues(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(c.controlRadius)).background(c.accentSoft),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Add, null, tint = c.accent) }
                                Column(Modifier.weight(1f)) {
                                    Text("Создать свою цветовую тему", style = MaterialTheme.typography.titleSmall, color = c.text)
                                    Text("Выберите цвет акцента, второго акцента и фона с живым предпросмотром", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customThemes.forEach { ct ->
                                val sel = s.activeCustomThemeId == ct.id
                                FlatCard(
                                    Modifier.fillMaxWidth(),
                                    color = if (sel) c.accentSoft else c.surface,
                                    onClick = { vm.selectCustomTheme(ct.id) },
                                    padding = PaddingValues(10.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(c.controlRadius))
                                                .background(Color(ct.bgArgb))
                                                .border(1.5.dp, Color(ct.accentArgb), RoundedCornerShape(c.controlRadius)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(Modifier.size(16.dp).clip(CircleShape).background(Color(ct.accentArgb)))
                                        }
                                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(ct.name, style = MaterialTheme.typography.titleSmall, color = if (sel) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (ct.isDark) "Тёмная · своя тема" else "Светлая · своя тема", style = MaterialTheme.typography.bodySmall, color = c.textDim)
                                        }
                                        IconButton(onClick = { editingTheme = ct; showEditor = true }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Rounded.Edit, "Изменить", tint = c.textDim, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { vm.deleteCustomTheme(ct.id) }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = c.danger, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // ===================== ВСТРОЕННЫЕ ЦВЕТОВЫЕ СХЕМЫ =====================
                item {
                    SectionHeader("Готовые цветовые схемы")
                    val themes = AuraTheme.entries.filter { it != AuraTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        themes.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { t ->
                                    val isSel = s.activeCustomThemeId.isBlank() && !s.adaptive && s.theme == t
                                    ThemePreviewCard(t, isSel, Modifier.weight(1f)) { vm.setTheme(t) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                // ===================== ВИЗУАЛИЗАЦИЯ =====================
                item {
                    SectionHeader("Визуализация частот (8 режимов)")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VisualizerType.entries.forEach { vt ->
                            FlatChip(vt.title, s.visualizer == vt, onClick = { vm.setVisualizer(vt) })
                        }
                    }
                    SectionHeader("Экран плеера по умолчанию")
                    FlatSegmentedControl(
                        options = listOf("Кассета", "Обложка", "Визуал"),
                        selectedIndex = s.playerStage,
                        onSelect = { vm.setPlayerStage(it) },
                    )
                }
                item {
                    SectionHeader("Дополнительно")
                    FlatCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            ToggleRow("Чёрный AMOLED-фон", "Чисто-чёрный фон в тёмном режиме", s.amoled) { vm.setAmoled(it) }
                            HorizontalDivider(color = c.border)
                            ToggleRow("Акцент из обложки", "Автоматически подстраивать цвет под обложку текущей песни (перекрывает тему)", s.adaptive) {
                                vm.setAdaptive(it)
                            }
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
                                Text("Как добавлять папки с SD-карты", style = MaterialTheme.typography.titleSmall, color = c.text)
                                Text(
                                    "1. Нажмите «Папка / SD» в разделе «Моя музыка».\n" +
                                        "2. Если выбрали корень карты памяти — откроется встроенный проводник: перейдите в нужную папку и нажмите «Добавить только эту папку».\n" +
                                        "3. Файлы не копируются (хранятся только пути). Форматы m4a/mp4 исключены.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textDim,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "PoliSANPlayer 1.5 · 2026\nKotlin · Jetpack Compose · Media3 · 32-bit DSP",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        }
    }

    if (showEditor) {
        CustomThemeEditorSheet(
            vm = vm,
            initial = editingTheme,
            onDismiss = {
                vm.previewCustomTheme.value = null
                showEditor = false
            },
        )
    }
    if (sleepDialog) SleepDialog(sleepAt) { sleepDialog = false }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.surfaceSolid,
            title = { Text("Очистить список?", color = c.text) },
            text = { Text("Все добавленные треки будут убраны из плеера. Файлы на телефоне останутся нетронутыми.", color = c.textDim) },
            confirmButton = {
                TextButton(onClick = { vm.clearLibrary(); confirmClear = false }) { Text("Очистить", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}

// ===================== Редактор пользовательских тем с живым предпросмотром =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomThemeEditorSheet(
    vm: MainViewModel,
    initial: CustomTheme?,
    onDismiss: () -> Unit,
) {
    val c = LocalAura.current
    val accentPresets = remember {
        listOf(
            Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFF84CC16),
            Color(0xFFF59E0B), Color(0xFFEA580C), Color(0xFFEF4444), Color(0xFFEC4899),
            Color(0xFFA855F7), Color(0xFF6366F1), Color(0xFF14B8A6), Color(0xFFE5E7EB),
        )
    }
    val darkBgs = remember {
        listOf(
            Color(0xFF0A0B10), Color(0xFF000000), Color(0xFF0C1222), Color(0xFF120E1D),
            Color(0xFF0B1915), Color(0xFF1C1214), Color(0xFF171410), Color(0xFF161922),
        )
    }
    val lightBgs = remember {
        listOf(
            Color(0xFFF4F5F8), Color(0xFFFFFFFF), Color(0xFFF0F4FA), Color(0xFFF5F0FA),
            Color(0xFFEEF7F3), Color(0xFFFAF3EE), Color(0xFFFDF2F4), Color(0xFFECEAE2),
        )
    }

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var isDark by remember { mutableStateOf(initial?.isDark ?: true) }

    val initHsv = remember(initial) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(initial?.accentArgb ?: Color(0xFF38BDF8).toArgb(), it)
        }
    }
    val initSecHsv = remember(initial) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(initial?.secondaryArgb ?: Color(0xFF818CF8).toArgb(), it)
        }
    }

    var hue1 by remember { mutableFloatStateOf(initHsv[0]) }
    var sat1 by remember { mutableFloatStateOf(initHsv[1].coerceIn(0.3f, 1f)) }
    var hue2 by remember { mutableFloatStateOf(initSecHsv[0]) }
    var bgColor by remember { mutableStateOf(Color(initial?.bgArgb ?: darkBgs.first().toArgb())) }

    val accentCol = Color.hsv(hue1, sat1, if (isDark) 0.95f else 0.78f)
    val secCol = Color.hsv(hue2, (sat1 * 0.9f).coerceIn(0.3f, 1f), if (isDark) 0.96f else 0.80f)

    val draft = CustomTheme(
        id = initial?.id ?: ("ct_" + System.currentTimeMillis()),
        name = name,
        accentArgb = accentCol.toArgb(),
        secondaryArgb = secCol.toArgb(),
        bgArgb = bgColor.toArgb(),
        isDark = isDark,
    )

    // Живой предпросмотр прямо на экране!
    LaunchedEffect(draft) { vm.previewCustomTheme.value = draft }
    DisposableEffect(Unit) { onDispose { vm.previewCustomTheme.value = null } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceSolid) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "Новая пользовательская тема" else "Редактировать тему", style = MaterialTheme.typography.titleLarge, color = c.text)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название темы (например, Неон Ночь)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FlatSegmentedControl(
                options = listOf("Тёмная", "Светлая"),
                selectedIndex = if (isDark) 0 else 1,
                onSelect = { idx ->
                    val newDark = idx == 0
                    if (newDark != isDark) {
                        isDark = newDark
                        bgColor = if (newDark) darkBgs.first() else lightBgs.first()
                    }
                },
            )

            Text("Основной акцент", style = MaterialTheme.typography.titleSmall, color = c.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accentPresets.forEach { col ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(2.dp, if (col.toArgb() == accentCol.toArgb()) c.text else Color.Transparent, CircleShape)
                            .clickable {
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(col.toArgb(), hsv)
                                hue1 = hsv[0]
                                sat1 = hsv[1].coerceIn(0.15f, 1f)
                            },
                    )
                }
            }
            Slider(
                value = hue1,
                onValueChange = { hue1 = it },
                valueRange = 0f..359f,
                colors = SliderDefaults.colors(thumbColor = accentCol, activeTrackColor = accentCol),
            )
            LabeledSlider("Насыщенность", "${(sat1 * 100).toInt()}%", sat1, 0.1f..1f, 0) { sat1 = it }

            Text("Второй акцент (визуализатор и градиенты)", style = MaterialTheme.typography.titleSmall, color = c.text)
            Slider(
                value = hue2,
                onValueChange = { hue2 = it },
                valueRange = 0f..359f,
                colors = SliderDefaults.colors(thumbColor = secCol, activeTrackColor = secCol),
            )

            Text("Цвет фона", style = MaterialTheme.typography.titleSmall, color = c.text)
            val bgList = if (isDark) darkBgs else lightBgs
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bgList.forEach { col ->
                    val sel = col.toArgb() == bgColor.toArgb()
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(col)
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) accentCol else c.border, RoundedCornerShape(10.dp))
                            .clickable { bgColor = col },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sel) Icon(Icons.Rounded.Check, null, tint = accentCol, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            FlatPrimaryButton("Сохранить и применить", Icons.Rounded.Check, {
                vm.saveCustomTheme(draft)
                onDismiss()
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StylePreviewCard(st: UiStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val radius = when (st) {
        UiStyle.FLAT -> 12.dp
        UiStyle.GLASS -> 16.dp
        UiStyle.SOFT -> 20.dp
        UiStyle.RETRO -> 4.dp
    }
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surface)
            .border(if (selected) 2.dp else c.borderWidth, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                st.title,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = if (st == UiStyle.RETRO) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Bold,
                color = if (selected) c.accent else c.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (selected) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(12.dp))
                }
            }
        }
        Text(
            st.desc,
            style = MaterialTheme.typography.bodySmall,
            color = c.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThemePreviewCard(t: AuraTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(c.cardRadius)
    val previewAccent = if (t == AuraTheme.MONO) (if (c.dark) Color(0xFFF4F5F8) else Color(0xFF14161D)) else t.accent
    val previewBg = if (c.dark) Color(0xFF0D0E12) else Color(0xFFF3F4F7)
    val previewCard = if (c.dark) Color(0xFF1B1E27) else Color(0xFFFFFFFF)

    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surface)
            .border(if (selected) 2.dp else c.borderWidth, if (selected) c.accent else c.border, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(c.controlRadius))
                .background(previewBg)
                .border(1.dp, c.border, RoundedCornerShape(c.controlRadius))
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
            Modifier.size(36.dp).clip(RoundedCornerShape(c.controlRadius)).background(if (danger) c.danger.copy(alpha = 0.14f) else c.accentSoft),
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

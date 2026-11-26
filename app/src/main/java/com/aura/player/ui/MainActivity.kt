package com.aura.player.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var openPickerAfterPerm = mutableStateOf(false)

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Разрешение на чтение аудио запрашивается ТОЛЬКО если пользователь сам открыл «Выбрать папки с устройства»
    private val pickerPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || checkAudioPerm()) {
            vm.loadDeviceFolders()
            openPickerAfterPerm.value = true
        } else {
            vm.toast.value = "Нет доступа к памяти. Используйте «Добавить файлы» или «Добавить папку»."
        }
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AudioEngine.init(this)

        // Запрашиваем только уведомления (для управления фоновым воспроизведением), память сами не сканируем!
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsState()
            val accentInt by AudioEngine.accent.collectAsState()
            val adaptive = if (settings.adaptive && settings.theme != AuraTheme.MONO) accentInt?.let { Color(it) } else null

            AuraAppTheme(settings.theme, settings.darkMode, settings.amoled, adaptive) { dark ->
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }

                AppRoot(
                    vm = vm,
                    showPickerSignal = openPickerAfterPerm.value,
                    onConsumePickerSignal = { openPickerAfterPerm.value = false },
                    onAddFiles = {
                        runCatching {
                            pickFiles.launch(arrayOf("audio/*", "application/ogg", "application/x-flac", "application/octet-stream", "video/mp4"))
                        }
                    },
                    onAddFolder = { runCatching { pickFolder.launch(null) } },
                    onRequestDevicePicker = {
                        if (checkAudioPerm()) {
                            vm.loadDeviceFolders()
                            openPickerAfterPerm.value = true
                        } else {
                            pickerPermLauncher.launch(audioPermission)
                        }
                    },
                )
            }
        }
    }

    private fun checkAudioPerm() = ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { vm.openExternal(it) }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.openExternal(it) }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.addFiles(it) }
        }
        intent.action = null
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    LIBRARY("Музыка", Icons.Rounded.LibraryMusic),
    RADIO("Радио", Icons.Rounded.Radio),
    SOUND("Звук", Icons.Rounded.Equalizer),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}

@Composable
private fun AppRoot(
    vm: MainViewModel,
    showPickerSignal: Boolean,
    onConsumePickerSignal: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onRequestDevicePicker: () -> Unit,
) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var soundSection by rememberSaveable { mutableIntStateOf(0) }     // 0 = Голос, 1 = Эквалайзер
    var settingsSection by rememberSaveable { mutableIntStateOf(0) }  // 0 = Темы, 1 = Общие
    var full by rememberSaveable { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    val now by AudioEngine.now.collectAsState()
    val sound by AudioEngine.sound.collectAsState()
    val settings by vm.settings.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    val error by AudioEngine.error.collectAsState()
    val toast by vm.toast.collectAsState()

    LaunchedEffect(showPickerSignal) {
        if (showPickerSignal) {
            showPicker = true
            onConsumePickerSignal()
        }
    }
    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast.value = null } }
    LaunchedEffect(error) { error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }

    BackHandler(full) { full = false }
    BackHandler(!full && drawerState.isOpen) { scope.launch { drawerState.close() } }

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val closeMenu: () -> Unit = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !full,
        drawerContent = {
            FlatDrawerMenu(
                tab = tab,
                soundSection = soundSection,
                settingsSection = settingsSection,
                trackCount = tracks.size,
                voiceLabel = if (sound.voice.ordinal != 0) sound.voice.title else null,
                eqLabel = if (sound.eqOn && sound.preset != "Ровно") sound.preset else null,
                themeLabel = settings.theme.title,
                sleepActive = sleepAt > 0,
                nowTitle = if (now.hasMedia) now.title else null,
                onNavigate = { t, sub ->
                    tab = t
                    if (t == Tab.SOUND) soundSection = sub
                    if (t == Tab.SETTINGS) settingsSection = sub
                    closeMenu()
                },
                onAddFiles = { closeMenu(); onAddFiles() },
                onAddFolder = { closeMenu(); onAddFolder() },
                onDevicePicker = { closeMenu(); onRequestDevicePicker() },
                onSleep = { closeMenu(); showSleep = true },
                onOpenPlayer = { if (now.hasMedia) { closeMenu(); full = true } },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            val bottomSpace = if (now.hasMedia) 156 else 92
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                    when (t) {
                        Tab.LIBRARY -> LibraryScreen(
                            vm = vm,
                            onMenu = openMenu,
                            onAddFiles = onAddFiles,
                            onAddFolder = onAddFolder,
                            onOpenDevicePicker = onRequestDevicePicker,
                            bottomPadding = bottomSpace,
                        )
                        Tab.RADIO -> RadioScreen(vm = vm, onMenu = openMenu, bottomPadding = bottomSpace)
                        Tab.SOUND -> SoundScreen(
                            section = soundSection,
                            onSectionChange = { soundSection = it },
                            onMenu = openMenu,
                            bottomPadding = bottomSpace,
                        )
                        Tab.SETTINGS -> SettingsScreen(
                            vm = vm,
                            section = settingsSection,
                            onSectionChange = { settingsSection = it },
                            onMenu = openMenu,
                            bottomPadding = bottomSpace,
                        )
                    }
                }
            }

            // ---- нижняя плоская док-панель: мини-плеер + навигация ----
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                AnimatedVisibility(
                    now.hasMedia,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(now, onOpen = { full = true })
                }
                FlatBottomBar(tab) { tab = it }
            }

            // ---- полноэкранный плеер ----
            AnimatedVisibility(
                full && now.hasMedia,
                enter = slideInVertically(tween(320)) { it } + fadeIn(),
                exit = slideOutVertically(tween(240)) { it } + fadeOut(),
            ) {
                FullPlayer(
                    vm = vm,
                    onClose = { full = false },
                    onOpenVoice = { full = false; tab = Tab.SOUND; soundSection = 0 },
                    onOpenEq = { full = false; tab = Tab.SOUND; soundSection = 1 },
                )
            }
        }
    }

    if (showPicker) {
        DeviceFolderPickerSheet(vm = vm, onDismiss = { showPicker = false })
    }
    if (showSleep) {
        SleepDialog(sleepAt) { showSleep = false }
    }
}

// ===================== Боковое Flat-меню =====================

@Composable
private fun FlatDrawerMenu(
    tab: Tab,
    soundSection: Int,
    settingsSection: Int,
    trackCount: Int,
    voiceLabel: String?,
    eqLabel: String?,
    themeLabel: String,
    sleepActive: Boolean,
    nowTitle: String?,
    onNavigate: (Tab, Int) -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onDevicePicker: () -> Unit,
    onSleep: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val c = LocalAura.current
    ModalDrawerSheet(
        modifier = Modifier.width(296.dp).fillMaxHeight(),
        drawerShape = RectangleShape,
        drawerContainerColor = c.surface,
        drawerTonalElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                // ---- заголовок меню ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                        .clickable(enabled = nowTitle != null, onClick = onOpenPlayer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Album, null, tint = c.onAccent, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Aura Player", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(
                            nowTitle ?: "Flat Edition · 2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (nowTitle != null) c.accent else c.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                DrawerLabel("НАВИГАЦИЯ")

                DrawerItem(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Моя музыка",
                    badge = trackCount.toString(),
                    selected = tab == Tab.LIBRARY,
                ) { onNavigate(Tab.LIBRARY, 0) }

                DrawerItem(
                    icon = Icons.Rounded.Radio,
                    title = "Онлайн-радио",
                    badge = "5 стран",
                    selected = tab == Tab.RADIO,
                ) { onNavigate(Tab.RADIO, 0) }

                DrawerItem(
                    icon = Icons.Rounded.RecordVoiceOver,
                    title = "Эффекты голоса",
                    badge = voiceLabel,
                    selected = tab == Tab.SOUND && soundSection == 0,
                ) { onNavigate(Tab.SOUND, 0) }

                DrawerItem(
                    icon = Icons.Rounded.Equalizer,
                    title = "Эквалайзер",
                    badge = eqLabel,
                    selected = tab == Tab.SOUND && soundSection == 1,
                ) { onNavigate(Tab.SOUND, 1) }

                DrawerItem(
                    icon = Icons.Rounded.Palette,
                    title = "Темы и вид",
                    badge = themeLabel,
                    selected = tab == Tab.SETTINGS && settingsSection == 0,
                ) { onNavigate(Tab.SETTINGS, 0) }

                DrawerItem(
                    icon = Icons.Rounded.Tune,
                    title = "Настройки",
                    selected = tab == Tab.SETTINGS && settingsSection == 1,
                ) { onNavigate(Tab.SETTINGS, 1) }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border)
                Spacer(Modifier.height(14.dp))
                DrawerLabel("ДОБАВИТЬ МУЗЫКУ")

                DrawerItem(icon = Icons.Rounded.Add, title = "Добавить файлы", selected = false, onClick = onAddFiles)
                DrawerItem(icon = Icons.Rounded.CreateNewFolder, title = "Добавить папку (SD)", selected = false, onClick = onAddFolder)
                DrawerItem(icon = Icons.Rounded.PhoneAndroid, title = "Выбрать папки с телефона", selected = false, onClick = onDevicePicker)

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border)
                Spacer(Modifier.height(14.dp))
                DrawerLabel("ИНСТРУМЕНТЫ")

                DrawerItem(
                    icon = Icons.Rounded.Bedtime,
                    title = "Таймер сна",
                    badge = if (sleepActive) "Вкл" else null,
                    selected = false,
                    onClick = onSleep,
                )
            }
            // правая плоская граница меню
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalAura.current.textDim,
        modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val c = LocalAura.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) c.accent else c.textDim, modifier = Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) c.accent else c.text,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!badge.isNullOrBlank()) {
            Text(
                badge,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) c.accent else c.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) c.surface else c.surface2)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                maxLines = 1,
            )
        }
    }
}

// ===================== Нижняя Flat-навигация =====================

@Composable
private fun FlatBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalAura.current
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val sel = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(t) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) c.accentSoft else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            t.icon,
                            t.title,
                            tint = if (sel) c.accent else c.textDim,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        t.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) c.accent else c.textDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

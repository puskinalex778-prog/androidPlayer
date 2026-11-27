package com.aura.player.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpeakerGroup
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.player.R
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var openPickerSignal = mutableStateOf<Int?>(null) // 0 = Файлы, 1 = Папки
    private var pendingPickerMode = 0
    private var pendingPlaylistId: String? = null

    private var sensorManager: SensorManager? = null
    private var shakeRegistered = false
    private var isActivityResumed = false
    private var lastShakeMs = 0L

    private val shakeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent) {
            val sens = vm.settings.value.shakeSensitivity
            val threshold = when (sens) {
                1 -> 2.9f
                2 -> 2.3f
                3 -> 1.85f
                else -> return
            }
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            val nowState = AudioEngine.now.value
            val nowMs = System.currentTimeMillis()
            if (g > threshold && nowState.isPlaying && !nowState.isRadio && nowMs - lastShakeMs > 1300L) {
                lastShakeMs = nowMs
                AudioEngine.next()
                Toast.makeText(this@MainActivity, "Следующий трек", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncShakeSensor() {
        if (shakeRegistered) {
            sensorManager?.unregisterListener(shakeListener)
            shakeRegistered = false
        }
        if (isActivityResumed && vm.settings.value.shakeSensitivity > 0) {
            val sm = sensorManager ?: (getSystemService(Context.SENSOR_SERVICE) as? SensorManager).also { sensorManager = it }
            val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm != null && acc != null) {
                shakeRegistered = sm.registerListener(shakeListener, acc, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val notifPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickerPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pl = pendingPlaylistId
        if (granted || checkAudioPerm()) {
            vm.loadDeviceFolders(targetPlaylistId = pl)
            openPickerSignal.value = pendingPickerMode
        } else {
            // Запасной вариант — системный выбор, если пользователь запретил доступ к музыке
            runCatching {
                pickFiles.launch(arrayOf("audio/mpeg", "audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/ogg", "application/ogg", "audio/opus", "audio/aac"))
            }
        }
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val pl = pendingPlaylistId
        pendingPlaylistId = null
        vm.addFiles(uris, targetPlaylistId = pl)
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val pl = pendingPlaylistId
        pendingPlaylistId = null
        if (uri != null) vm.openSafTree(uri, targetPlaylistId = pl)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AudioEngine.init(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsState()
            val customThemes by vm.customThemes.collectAsState()
            val previewCustom by vm.previewCustomTheme.collectAsState()
            val activeCustom = previewCustom ?: customThemes.firstOrNull { it.id == settings.activeCustomThemeId }
            val accentInt by AudioEngine.accent.collectAsState()
            val adaptive = if (settings.adaptive && activeCustom == null && settings.theme != AuraTheme.MONO) accentInt?.let { Color(it) } else null

            LaunchedEffect(settings.shakeSensitivity) { syncShakeSensor() }

            AuraAppTheme(settings.theme, activeCustom, settings.style, settings.darkMode, settings.amoled, adaptive) { dark ->
                val view = LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }

                AppRoot(
                    vm = vm,
                    pickerSignalMode = openPickerSignal.value,
                    onConsumePickerSignal = { openPickerSignal.value = null },
                    onAddFiles = { plId ->
                        // Открываем встроенный фильтр-выбор песен (показывает ТОЛЬКО музыку, без PNG/JPG/M4A/MP4!)
                        pendingPlaylistId = plId
                        pendingPickerMode = 0
                        if (checkAudioPerm()) {
                            vm.loadDeviceFolders(targetPlaylistId = plId)
                            openPickerSignal.value = 0
                        } else {
                            pickerPermLauncher.launch(audioPermission)
                        }
                    },
                    onAddFolder = { plId ->
                        pendingPlaylistId = plId
                        runCatching { pickFolder.launch(null) }
                    },
                    onRequestDevicePicker = {
                        pendingPlaylistId = null
                        pendingPickerMode = 1
                        if (checkAudioPerm()) {
                            vm.loadDeviceFolders(targetPlaylistId = null)
                            openPickerSignal.value = 1
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

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        syncShakeSensor()
    }

    override fun onPause() {
        isActivityResumed = false
        syncShakeSensor()
        super.onPause()
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
    pickerSignalMode: Int?,
    onConsumePickerSignal: () -> Unit,
    onAddFiles: (playlistId: String?) -> Unit,
    onAddFolder: (playlistId: String?) -> Unit,
    onRequestDevicePicker: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var libTab by rememberSaveable { mutableStateOf(LibTab.TRACKS) }
    var soundSection by rememberSaveable { mutableIntStateOf(0) }
    var settingsSection by rememberSaveable { mutableIntStateOf(0) }
    var full by rememberSaveable { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf<Int?>(null) }
    var showSleep by remember { mutableStateOf(false) }
    var showDockCustomizer by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showCarMode by rememberSaveable { mutableStateOf(false) }

    val now by AudioEngine.now.collectAsState()
    val sound by AudioEngine.sound.collectAsState()
    val output by AudioEngine.output.collectAsState()
    val settings by vm.settings.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val safState by vm.safBrowser.collectAsState()
    val sleepAt by AudioEngine.sleepAt.collectAsState()
    val sleepAfterTrack by AudioEngine.sleepAfterTrack.collectAsState()
    val error by AudioEngine.error.collectAsState()
    val toast by vm.toast.collectAsState()

    LaunchedEffect(pickerSignalMode) {
        if (pickerSignalMode != null) {
            pickerMode = pickerSignalMode
            onConsumePickerSignal()
        }
    }
    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast.value = null } }
    LaunchedEffect(error) { error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }

    BackHandler(!full && !showCarMode && drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(full && !showCarMode) { full = false }
    BackHandler(showCarMode) { showCarMode = false }

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val closeMenu: () -> Unit = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !full && !showCarMode,
        drawerContent = {
            FlatDrawerMenu(
                tab = tab,
                libTab = libTab,
                soundSection = soundSection,
                settingsSection = settingsSection,
                trackCount = tracks.size,
                playlistCount = playlists.size,
                voiceLabel = if (sound.voice.ordinal != 0) sound.voice.title else null,
                eqLabel = if (sound.eqOn && sound.preset != "Ровно") sound.preset else null,
                outputLabel = output.activeLabel,
                styleLabel = settings.style.title,
                sleepActive = sleepAt > 0 || sleepAfterTrack,
                dockCount = settings.dockActions.size,
                nowTitle = if (now.hasMedia) now.title else null,
                onNavigate = { t, sub, lt ->
                    tab = t
                    if (lt != null) libTab = lt
                    if (t == Tab.SOUND) soundSection = sub
                    if (t == Tab.SETTINGS) settingsSection = sub
                    closeMenu()
                },
                onAddFiles = { closeMenu(); onAddFiles(null) },
                onAddFolder = { closeMenu(); onAddFolder(null) },
                onDevicePicker = { closeMenu(); onRequestDevicePicker() },
                onDockCustomizer = { closeMenu(); showDockCustomizer = true },
                onCarMode = { closeMenu(); showCarMode = true },
                onStats = { closeMenu(); showStats = true },
                onSleep = { closeMenu(); showSleep = true },
                onOpenPlayer = { if (now.hasMedia) { closeMenu(); full = true } },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            StyleBackdrop()

            val bottomSpace = if (now.hasMedia) 156 else 92
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                    when (t) {
                        Tab.LIBRARY -> LibraryScreen(
                            vm = vm,
                            tab = libTab,
                            onTabChange = { libTab = it },
                            onMenu = openMenu,
                            onAddFiles = onAddFiles,
                            onAddFolder = onAddFolder,
                            onOpenDevicePicker = onRequestDevicePicker,
                            bottomPadding = bottomSpace,
                        )
                        Tab.RADIO -> RadioScreen(vm = vm, onMenu = openMenu, bottomPadding = bottomSpace)
                        Tab.SOUND -> SoundScreen(
                            vm = vm,
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
                            onOpenDockCustomizer = { showDockCustomizer = true },
                            onOpenCarMode = { showCarMode = true },
                            onOpenStats = { showStats = true },
                        )
                    }
                }
            }

            // ---- нижняя док-панель ----
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
                    onOpenRadio = { full = false; tab = Tab.RADIO },
                    onOpenPlaylists = { full = false; tab = Tab.LIBRARY; libTab = LibTab.PLAYLISTS },
                    onOpenCarMode = { showCarMode = true },
                    onCustomizeDock = { showDockCustomizer = true },
                )
            }

            AnimatedVisibility(
                showCarMode,
                enter = slideInVertically(tween(260)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(),
            ) {
                CarModeOverlay(vm = vm, onClose = { showCarMode = false })
            }
        }
    }

    safState?.let { st ->
        SafFolderBrowserSheet(vm = vm, state = st)
    }
    pickerMode?.let { mode ->
        DeviceFolderPickerSheet(
            vm = vm,
            initialMode = mode,
            onOpenSdFolder = { onAddFolder(vm.pickerTargetPlaylistId.value) },
            onDismiss = { pickerMode = null },
        )
    }
    if (showSleep) {
        SleepDialog(sleepAt) { showSleep = false }
    }
    if (showDockCustomizer) {
        DockCustomizerSheet(vm = vm, onDismiss = { showDockCustomizer = false })
    }
    if (showStats) {
        StatsSheet(vm = vm, onDismiss = { showStats = false })
    }
}

// ===================== Боковое меню =====================

@Composable
private fun FlatDrawerMenu(
    tab: Tab,
    libTab: LibTab,
    soundSection: Int,
    settingsSection: Int,
    trackCount: Int,
    playlistCount: Int,
    voiceLabel: String?,
    eqLabel: String?,
    outputLabel: String,
    styleLabel: String,
    sleepActive: Boolean,
    dockCount: Int,
    nowTitle: String?,
    onNavigate: (Tab, Int, LibTab?) -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onDevicePicker: () -> Unit,
    onDockCustomizer: () -> Unit,
    onCarMode: () -> Unit,
    onStats: () -> Unit,
    onSleep: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val c = LocalAura.current
    ModalDrawerSheet(
        modifier = Modifier.width(296.dp).fillMaxHeight(),
        drawerShape = RectangleShape,
        drawerContainerColor = c.surfaceSolid,
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(c.cardRadius))
                        .background(c.surface2)
                        .border(c.borderWidth, c.border, RoundedCornerShape(c.cardRadius))
                        .clickable(enabled = nowTitle != null, onClick = onOpenPlayer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_polisan_logo),
                        contentDescription = "PoliSANPlayer",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(c.controlRadius)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("PoliSANPlayer", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(
                            nowTitle ?: "$styleLabel · 2026",
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
                    selected = tab == Tab.LIBRARY && libTab != LibTab.PLAYLISTS,
                ) { onNavigate(Tab.LIBRARY, 0, LibTab.TRACKS) }

                DrawerItem(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Плейлисты",
                    badge = playlistCount.toString(),
                    selected = tab == Tab.LIBRARY && libTab == LibTab.PLAYLISTS,
                ) { onNavigate(Tab.LIBRARY, 0, LibTab.PLAYLISTS) }

                DrawerItem(
                    icon = Icons.Rounded.Radio,
                    title = "Онлайн-радио",
                    badge = "5 стран",
                    selected = tab == Tab.RADIO,
                ) { onNavigate(Tab.RADIO, 0, null) }

                DrawerItem(
                    icon = Icons.Rounded.RecordVoiceOver,
                    title = "Эффекты голоса",
                    badge = voiceLabel,
                    selected = tab == Tab.SOUND && soundSection == 0,
                ) { onNavigate(Tab.SOUND, 0, null) }

                DrawerItem(
                    icon = Icons.Rounded.Equalizer,
                    title = "Эквалайзер",
                    badge = eqLabel,
                    selected = tab == Tab.SOUND && soundSection == 1,
                ) { onNavigate(Tab.SOUND, 1, null) }

                DrawerItem(
                    icon = Icons.Rounded.SpeakerGroup,
                    title = "Вывод и Hi-Res",
                    badge = outputLabel,
                    selected = tab == Tab.SOUND && soundSection == 2,
                ) { onNavigate(Tab.SOUND, 2, null) }

                DrawerItem(
                    icon = Icons.Rounded.Palette,
                    title = "Темы и стили",
                    badge = styleLabel,
                    selected = tab == Tab.SETTINGS && settingsSection == 0,
                ) { onNavigate(Tab.SETTINGS, 0, null) }

                DrawerItem(
                    icon = Icons.Rounded.Tune,
                    title = "Настройки",
                    selected = tab == Tab.SETTINGS && settingsSection == 1,
                ) { onNavigate(Tab.SETTINGS, 1, null) }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border)
                Spacer(Modifier.height(14.dp))
                DrawerLabel("ДОБАВИТЬ МУЗЫКУ")

                DrawerItem(icon = Icons.Rounded.Add, title = "Добавить файлы", selected = false, onClick = onAddFiles)
                DrawerItem(icon = Icons.Rounded.CreateNewFolder, title = "Выбрать папку (SD)", selected = false, onClick = onAddFolder)
                DrawerItem(icon = Icons.Rounded.PhoneAndroid, title = "Импорт папок с телефона", selected = false, onClick = onDevicePicker)

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border)
                Spacer(Modifier.height(14.dp))
                DrawerLabel("ИНСТРУМЕНТЫ")

                DrawerItem(
                    icon = Icons.Rounded.Tune,
                    title = "Нижняя панель плеера",
                    badge = "$dockCount кн.",
                    selected = false,
                    onClick = onDockCustomizer,
                )
                DrawerItem(
                    icon = Icons.Rounded.DirectionsCar,
                    title = "Режим Авто",
                    selected = false,
                    onClick = onCarMode,
                )
                DrawerItem(
                    icon = Icons.Rounded.BarChart,
                    title = "Статистика",
                    selected = false,
                    onClick = onStats,
                )
                DrawerItem(
                    icon = Icons.Rounded.Bedtime,
                    title = "Таймер сна",
                    badge = if (sleepActive) "Вкл" else null,
                    selected = false,
                    onClick = onSleep,
                )
            }
            Box(Modifier.width(c.borderWidth).fillMaxHeight().background(c.border))
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
    val shape = RoundedCornerShape(c.controlRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    .background(if (selected) c.surfaceSolid else c.surface2)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                maxLines = 1,
            )
        }
    }
}

// ===================== Нижняя навигация =====================

@Composable
private fun FlatBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalAura.current
    val bg = if (c.style == UiStyle.GLASS) c.surfaceSolid.copy(alpha = 0.88f) else c.surface
    Column(Modifier.fillMaxWidth().background(bg)) {
        HorizontalDivider(thickness = c.borderWidth, color = c.border)
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
                        .clip(RoundedCornerShape(c.controlRadius))
                        .clickable { onSelect(t) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(c.controlRadius))
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

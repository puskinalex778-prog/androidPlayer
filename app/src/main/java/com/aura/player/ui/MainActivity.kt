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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.player.playback.AudioEngine
import com.aura.player.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val hasPermission = mutableStateOf(false)

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasPermission.value = res[audioPermission] == true || checkPerm()
        vm.rescan()
    }

    // Выбор файлов: несколько сразу, видны и файлы на карте памяти
    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AudioEngine.init(this)
        hasPermission.value = checkPerm()

        val need = mutableListOf<String>()
        if (!hasPermission.value) need += audioPermission
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray()) else vm.rescan()

        handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsState()
            val accentInt by AudioEngine.accent.collectAsState()
            val adaptive = if (settings.adaptive && settings.theme != AuraTheme.MONO) accentInt?.let { Color(it) } else null
            AuraAppTheme(settings.theme, settings.darkMode, settings.amoled, adaptive) {
                AppRoot(
                    vm = vm,
                    hasPermission = hasPermission.value,
                    onRequestPermission = { permLauncher.launch(arrayOf(audioPermission)) },
                    onAddFiles = {
                        runCatching {
                            pickFiles.launch(arrayOf("audio/*", "application/ogg", "application/x-flac", "application/octet-stream", "video/mp4"))
                        }
                    },
                    onAddFolder = { runCatching { pickFolder.launch(null) } },
                )
            }
        }
    }

    private fun checkPerm() = ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

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
        // Подключение к фоновому сервису — он держит музыку при свёрнутом приложении
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
    SOUND("Звук", Icons.Rounded.GraphicEq),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}

@Composable
private fun AppRoot(
    vm: MainViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
) {
    val c = LocalAura.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var full by rememberSaveable { mutableStateOf(false) }
    val now by AudioEngine.now.collectAsState()
    val error by AudioEngine.error.collectAsState()
    val toast by vm.toast.collectAsState()

    LaunchedEffect(toast) { toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast.value = null } }
    LaunchedEffect(error) { error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }

    BackHandler(full) { full = false }

    Box(Modifier.fillMaxSize()) {
        AuraBackground()
        val bottom = if (now.hasMedia) 190 else 110
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                when (t) {
                    Tab.LIBRARY -> LibraryScreen(vm, hasPermission, onRequestPermission, onAddFiles, onAddFolder, bottom)
                    Tab.RADIO -> RadioScreen(vm, bottom)
                    Tab.SOUND -> SoundScreen(bottom)
                    Tab.SETTINGS -> SettingsScreen(vm, bottom)
                }
            }
        }

        // ---- мини-плеер + навигация ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedVisibility(now.hasMedia, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                MiniPlayer(now, onOpen = { full = true })
            }
            NavBar(tab) { tab = it }
        }

        // ---- полноэкранный плеер ----
        AnimatedVisibility(
            full && now.hasMedia,
            enter = slideInVertically(tween(380)) { it } + fadeIn(),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(),
        ) {
            FullPlayer(vm, onClose = { full = false }, onOpenSound = { full = false; tab = Tab.SOUND })
        }
    }
}

@Composable
private fun NavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalAura.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Tab.entries.forEach { t ->
                val sel = t == selected
                Row(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (sel) Brush.horizontalGradient(listOf(c.accent, c.accent2)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                        .clickable { onSelect(t) }
                        .padding(horizontal = if (sel) 16.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, t.title, tint = if (sel) MaterialTheme.colorScheme.onPrimary else c.textDim, modifier = Modifier.size(22.dp))
                    AnimatedVisibility(sel) {
                        Text(t.title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

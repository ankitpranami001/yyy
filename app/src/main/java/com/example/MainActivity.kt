package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WallpaperTarget
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.LiveHtmlTheme
import com.example.wallpaper.WallpaperPickerLauncher

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveHtmlRoot(
                viewModel = viewModel,
                onLaunchWallpaperPicker = { launchSystemLiveWallpaperPicker() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWallpaperServiceStatus()
    }

    private fun launchSystemLiveWallpaperPicker() {
        when (WallpaperPickerLauncher.launch(this)) {
            WallpaperPickerLauncher.LaunchResult.OPENED_DIRECT,
            WallpaperPickerLauncher.LaunchResult.OPENED_CHOOSER -> Unit
            WallpaperPickerLauncher.LaunchResult.OPENED_GENERIC_WALLPAPER_SETTINGS -> {
                viewModel.showMessage(getString(R.string.chooser_select_wallpaper))
            }
            WallpaperPickerLauncher.LaunchResult.NOT_SUPPORTED_BY_OS -> {
                viewModel.showMessage(getString(R.string.msg_wallpaper_not_supported))
            }
            WallpaperPickerLauncher.LaunchResult.FAILED -> {
                viewModel.showMessage(getString(R.string.msg_wallpaper_picker_error))
            }
        }
    }
}

@Composable
private fun LiveHtmlRoot(
    viewModel: MainViewModel,
    onLaunchWallpaperPicker: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val htmlOpenDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importHtml(uri) }

    val htmlGetContentFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.importHtml(uri) }

    val videoPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.importVideo(uri) }

    val videoOpenDocFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importVideo(uri) }

    val videoGetContentFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.importVideo(uri) }

    val gifOpenDocPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importGif(uri) }

    val gifGetContentFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.importGif(uri) }

    fun launchHtmlPicker() {
        val launched = runCatching {
            htmlOpenDocPicker.launch(
                arrayOf(
                    "text/html",
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                    "*/*"
                )
            )
            true
        }.getOrDefault(false)
        if (!launched) {
            val fallbackOk = runCatching {
                htmlGetContentFallback.launch("*/*")
                true
            }.getOrDefault(false)
            if (!fallbackOk) {
                viewModel.showMessage(context.getString(R.string.msg_file_picker_error))
            }
        }
    }

    fun launchVideoPicker() {
        val launched = runCatching {
            videoPhotoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
            true
        }.getOrDefault(false)
        if (!launched) {
            val openDocOk = runCatching {
                videoOpenDocFallback.launch(arrayOf("video/*", "*/*"))
                true
            }.getOrDefault(false)
            if (!openDocOk) {
                val getContentOk = runCatching {
                    videoGetContentFallback.launch("video/*")
                    true
                }.getOrDefault(false)
                if (!getContentOk) {
                    viewModel.showMessage(context.getString(R.string.msg_file_picker_error))
                }
            }
        }
    }

    fun launchGifPicker() {
        val launched = runCatching {
            gifOpenDocPicker.launch(arrayOf("image/gif", "image/*", "*/*"))
            true
        }.getOrDefault(false)
        if (!launched) {
            val fallbackOk = runCatching {
                gifGetContentFallback.launch("image/gif")
                true
            }.getOrDefault(false)
            if (!fallbackOk) {
                viewModel.showMessage(context.getString(R.string.msg_file_picker_error))
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LiveHtmlTheme(darkTheme = uiState.settings.darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            HomeScreen(
                uiState = uiState,
                repository = viewModel.repository,
                onUploadHtml = ::launchHtmlPicker,
                onUploadVideo = ::launchVideoPicker,
                onUploadGif = ::launchGifPicker,
                onOpenApplySheet = { viewModel.setApplySheetVisible(true) },
                onDismissApplySheet = { viewModel.setApplySheetVisible(false) },
                onApplyForTarget = { target: WallpaperTarget ->
                    viewModel.selectWallpaperTarget(target)
                    onLaunchWallpaperPicker()
                },
                onPreviewStyleChange = viewModel::setPreviewStyle,
                onToggleSound = viewModel::setSoundEnabled,
                onToggleTheme = viewModel::toggleDarkTheme,
                onOpenSettings = { viewModel.setSettingsSheetVisible(true) },
                onDismissSettings = { viewModel.setSettingsSheetVisible(false) },
                onVolumeChange = viewModel::setVolume,
                onMuteOtherMediaChange = viewModel::setMuteDuringOtherMedia,
                onTouchEnabledChange = viewModel::setTouchEnabled,
                onRenderModeChange = viewModel::setRenderMode,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

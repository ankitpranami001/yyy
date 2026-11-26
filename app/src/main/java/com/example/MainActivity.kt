package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.WallpaperTarget
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.LiveHtmlTheme
import com.example.wallpaper.HtmlLiveWallpaperService

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
        val component = ComponentName(this, HtmlLiveWallpaperService::class.java)
        val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        try {
            startActivity(directIntent)
        } catch (_: Throwable) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Throwable) {
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val htmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importHtml(uri)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importVideo(uri)
    }

    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importGif(uri)
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
                onUploadHtml = {
                    htmlPicker.launch(
                        arrayOf(
                            "text/html",
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onUploadVideo = {
                    videoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onUploadGif = {
                    gifPicker.launch(arrayOf("image/gif"))
                },
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

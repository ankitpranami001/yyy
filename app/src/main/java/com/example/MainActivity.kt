package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.screens.CodeLabScreen
import com.example.ui.screens.ControlsScreen
import com.example.ui.screens.FullscreenPreviewOverlay
import com.example.ui.screens.GalleryScreen
import com.example.ui.theme.LiveHtmlTheme
import com.example.wallpaper.HtmlLiveWallpaperService

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveHtmlTheme {
                LiveHtmlApp(
                    viewModel = viewModel,
                    onLaunchWallpaperPicker = { launchSystemLiveWallpaperPicker() }
                )
            }
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
private fun LiveHtmlApp(
    viewModel: MainViewModel,
    onLaunchWallpaperPicker: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importHtmlOrZip(uri)
        }
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val launchFilePicker: () -> Unit = {
        openDocumentLauncher.launch(
            arrayOf(
                "text/html",
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            NavigationBarItem(
                                selected = uiState.selectedTab == AppTab.GALLERY,
                                onClick = { viewModel.selectTab(AppTab.GALLERY) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.GALLERY) {
                                            Icons.Filled.Collections
                                        } else {
                                            Icons.Outlined.Collections
                                        },
                                        contentDescription = stringResource(R.string.nav_gallery)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_gallery)) },
                                modifier = Modifier.testTag("nav_gallery")
                            )
                            NavigationBarItem(
                                selected = uiState.selectedTab == AppTab.CODE_LAB,
                                onClick = { viewModel.selectTab(AppTab.CODE_LAB) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.CODE_LAB) {
                                            Icons.Filled.Code
                                        } else {
                                            Icons.Outlined.Code
                                        },
                                        contentDescription = stringResource(R.string.nav_codelab)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_codelab)) },
                                modifier = Modifier.testTag("nav_codelab")
                            )
                            NavigationBarItem(
                                selected = uiState.selectedTab == AppTab.CONTROLS,
                                onClick = { viewModel.selectTab(AppTab.CONTROLS) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.CONTROLS) {
                                            Icons.Filled.Tune
                                        } else {
                                            Icons.Outlined.Tune
                                        },
                                        contentDescription = stringResource(R.string.nav_controls)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_controls)) },
                                modifier = Modifier.testTag("nav_controls")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            NavigationRailItem(
                                selected = uiState.selectedTab == AppTab.GALLERY,
                                onClick = { viewModel.selectTab(AppTab.GALLERY) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.GALLERY) {
                                            Icons.Filled.Collections
                                        } else {
                                            Icons.Outlined.Collections
                                        },
                                        contentDescription = stringResource(R.string.nav_gallery)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_gallery)) },
                                modifier = Modifier.testTag("rail_gallery")
                            )
                            NavigationRailItem(
                                selected = uiState.selectedTab == AppTab.CODE_LAB,
                                onClick = { viewModel.selectTab(AppTab.CODE_LAB) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.CODE_LAB) {
                                            Icons.Filled.Code
                                        } else {
                                            Icons.Outlined.Code
                                        },
                                        contentDescription = stringResource(R.string.nav_codelab)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_codelab)) },
                                modifier = Modifier.testTag("rail_codelab")
                            )
                            NavigationRailItem(
                                selected = uiState.selectedTab == AppTab.CONTROLS,
                                onClick = { viewModel.selectTab(AppTab.CONTROLS) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.CONTROLS) {
                                            Icons.Filled.Tune
                                        } else {
                                            Icons.Outlined.Tune
                                        },
                                        contentDescription = stringResource(R.string.nav_controls)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_controls)) },
                                modifier = Modifier.testTag("rail_controls")
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (uiState.selectedTab) {
                            AppTab.GALLERY -> GalleryScreen(
                                uiState = uiState,
                                repository = viewModel.repository,
                                isPreviewActive = uiState.fullscreenPreviewItem == null,
                                onUploadClick = launchFilePicker,
                                onSetLiveWallpaperClick = onLaunchWallpaperPicker,
                                onSelectWallpaper = viewModel::selectWallpaper,
                                onOpenFullscreen = viewModel::openFullscreenPreview,
                                onEditInCodeLab = viewModel::openInCodeLab,
                                onDeleteWallpaper = viewModel::deleteWallpaper,
                                onToggleSound = viewModel::setSoundEnabled
                            )

                            AppTab.CODE_LAB -> CodeLabScreen(
                                state = uiState.codeLab,
                                repository = viewModel.repository,
                                soundEnabled = uiState.settings.soundEnabled,
                                volume = uiState.settings.volume,
                                touchEnabled = uiState.settings.touchEnabled,
                                onNameChange = viewModel::updateCodeLabName,
                                onHtmlChange = viewModel::updateCodeLabHtml,
                                onTogglePreview = viewModel::setCodeLabPreviewVisible,
                                onLoadTemplate = viewModel::loadStarterTemplate,
                                onSaveAndSelect = viewModel::saveCodeLabWallpaper
                            )

                            AppTab.CONTROLS -> ControlsScreen(
                                settings = uiState.settings,
                                onSoundEnabledChange = viewModel::setSoundEnabled,
                                onVolumeChange = viewModel::setVolume,
                                onMuteOtherMediaChange = viewModel::setMuteDuringOtherMedia,
                                onTouchEnabledChange = viewModel::setTouchEnabled,
                                onRenderModeChange = viewModel::setRenderMode,
                                onTargetFpsChange = viewModel::setTargetFps
                            )
                        }
                    }
                }
            }
        }

        val fullscreen = uiState.fullscreenPreviewItem
        if (fullscreen != null) {
            FullscreenPreviewOverlay(
                item = fullscreen,
                repository = viewModel.repository,
                soundEnabled = uiState.settings.soundEnabled,
                volume = uiState.settings.volume,
                touchEnabled = uiState.settings.touchEnabled,
                reloadToken = uiState.previewReloadToken,
                onBack = viewModel::closeFullscreenPreview,
                onToggleSound = viewModel::setSoundEnabled,
                onEditCode = {
                    viewModel.closeFullscreenPreview()
                    viewModel.openInCodeLab(fullscreen)
                },
                onApplyWallpaper = {
                    viewModel.selectWallpaper(fullscreen)
                    viewModel.closeFullscreenPreview()
                    onLaunchWallpaperPicker()
                }
            )
        }
    }
}

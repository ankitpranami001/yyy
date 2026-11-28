package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RenderEngineMode
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSource
import com.example.data.WallpaperTarget
import com.example.ui.MainUiState
import com.example.ui.PhonePreviewStyle
import com.example.ui.components.DualLayerWallpaperCanvas
import com.example.ui.components.PhonePreviewMockup
import com.example.ui.components.WallpaperSettingsSheetContent
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CoralGif
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.VioletVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    repository: WallpaperRepository,
    onUploadHtml: () -> Unit,
    onUploadVideo: () -> Unit,
    onUploadGif: () -> Unit,
    onUploadTheme: () -> Unit,
    onToggleThemeOverlay: () -> Unit,
    onClearTheme: () -> Unit,
    onOpenFullscreenPreview: () -> Unit,
    onDismissFullscreenPreview: () -> Unit,
    onOpenApplySheet: () -> Unit,
    onDismissApplySheet: () -> Unit,
    onApplyForTarget: (WallpaperTarget) -> Unit,
    onPreviewStyleChange: (PhonePreviewStyle) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteOtherMediaChange: (Boolean) -> Unit,
    onTouchEnabledChange: (Boolean) -> Unit,
    onThemeTransparentChange: (Boolean) -> Unit,
    onRenderModeChange: (RenderEngineMode) -> Unit,
    onLockScreenChangeEnabled: (Boolean) -> Unit = {},
    onUploadLockHtml: () -> Unit = {},
    onUploadLockVideo: () -> Unit = {},
    onUploadLockGif: () -> Unit = {},
    onUploadLockTheme: () -> Unit = {},
    onClearLockScreenCustom: () -> Unit = {},
    onApplyLockScreenNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeSource = uiState.activeWallpaper?.source
    val hasActiveTheme = uiState.activeTheme != null && uiState.settings.themeEnabled
    val isLockPreview = uiState.previewStyle == PhonePreviewStyle.LOCK_SCREEN
    val previewWallpaper = if (isLockPreview) {
        uiState.lockScreenWallpaper ?: uiState.activeWallpaper
    } else {
        uiState.activeWallpaper
    }
    val previewTheme = if (isLockPreview) {
        uiState.lockScreenTheme ?: uiState.activeTheme
    } else {
        uiState.activeTheme
    }
    val isCustomLockPreview = isLockPreview &&
        (uiState.lockScreenWallpaper != null || uiState.lockScreenTheme != null)
    val lockScreenDisabled = isLockPreview &&
        uiState.settings.wallpaperTarget == WallpaperTarget.HOME_ONLY

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimal clean header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ankit_logo),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (uiState.isServiceActiveOnHomeScreen) {
                        val activeLabel = when (uiState.settings.wallpaperTarget) {
                            WallpaperTarget.HOME_ONLY -> stringResource(R.string.status_active_home)
                            WallpaperTarget.LOCK_ONLY -> stringResource(R.string.status_active_lock)
                            WallpaperTarget.BOTH -> stringResource(R.string.status_active_both)
                        }
                        Surface(
                            shape = CircleShape,
                            color = EmeraldLive.copy(alpha = 0.14f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldLive)
                                )
                                Text(
                                    text = activeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldLive
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.settings.darkTheme) {
                                Icons.Default.LightMode
                            } else {
                                Icons.Default.DarkMode
                            },
                            contentDescription = "Switch clean light or dark theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Clean Upload Buttons in one row: HTML, Video, GIF + Upload HTML Theme
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UploadTileButton(
                    title = stringResource(R.string.btn_upload_html),
                    subtitle = stringResource(R.string.label_live_wallpaper),
                    icon = Icons.Default.Code,
                    accent = CobaltAccent,
                    selected = activeSource == WallpaperSource.UPLOADED_HTML || activeSource == WallpaperSource.UPLOADED_ZIP,
                    enabled = !uiState.isImporting,
                    onClick = onUploadHtml,
                    testTag = "upload_html_button",
                    modifier = Modifier.weight(1f)
                )
                UploadTileButton(
                    title = stringResource(R.string.btn_upload_video),
                    subtitle = stringResource(R.string.label_live_wallpaper),
                    icon = Icons.Default.Videocam,
                    accent = VioletVideo,
                    selected = activeSource == WallpaperSource.UPLOADED_VIDEO,
                    enabled = !uiState.isImporting,
                    onClick = onUploadVideo,
                    testTag = "upload_video_button",
                    modifier = Modifier.weight(1f)
                )
                UploadTileButton(
                    title = stringResource(R.string.btn_upload_gif),
                    subtitle = stringResource(R.string.label_live_wallpaper),
                    icon = Icons.Default.GifBox,
                    accent = CoralGif,
                    selected = activeSource == WallpaperSource.UPLOADED_GIF,
                    enabled = !uiState.isImporting,
                    onClick = onUploadGif,
                    testTag = "upload_gif_button",
                    modifier = Modifier.weight(1f)
                )
                UploadTileButton(
                    title = stringResource(R.string.btn_upload_theme),
                    subtitle = stringResource(R.string.label_html_theme),
                    icon = Icons.Default.Palette,
                    accent = EmeraldLive,
                    selected = hasActiveTheme,
                    enabled = !uiState.isImporting,
                    onClick = onUploadTheme,
                    testTag = "upload_theme_button",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Centered Android Mobile Live Preview Mockup (Wallpaper + HTML Theme together)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PhonePreviewMockup(
                    activeWallpaper = previewWallpaper,
                    activeTheme = previewTheme,
                    themeEnabled = uiState.settings.themeEnabled,
                    themeTransparent = uiState.settings.themeTransparentBackground,
                    isCustomLockPreview = isCustomLockPreview,
                    lockScreenChangeDisabled = lockScreenDisabled,
                    repository = repository,
                    previewStyle = uiState.previewStyle,
                    soundEnabled = uiState.settings.soundEnabled,
                    volume = uiState.settings.volume,
                    touchEnabled = uiState.settings.touchEnabled,
                    reloadToken = uiState.previewReloadToken,
                    onPreviewStyleChange = onPreviewStyleChange,
                    onToggleSound = onToggleSound,
                    onToggleThemeOverlay = onToggleThemeOverlay,
                    onClearTheme = onClearTheme,
                    onOpenFullscreenPreview = onOpenFullscreenPreview,
                    modifier = Modifier.fillMaxSize()
                )

                if (uiState.isImporting) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Loading file...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Set Home / Lock Screen primary button
            Button(
                onClick = onOpenApplySheet,
                enabled = !uiState.isImporting,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("set_home_lock_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Wallpaper,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.btn_set_home_lock),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Fullscreen Mobile View Preview (shows uploaded HTML Theme + Wallpaper across the entire phone screen)
        if (uiState.showFullscreenPreview) {
            BackHandler(onBack = onDismissFullscreenPreview)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("fullscreen_mobile_view")
            ) {
                DualLayerWallpaperCanvas(
                    activeWallpaper = previewWallpaper,
                    activeTheme = previewTheme,
                    themeEnabled = uiState.settings.themeEnabled,
                    themeTransparent = uiState.settings.themeTransparentBackground,
                    repository = repository,
                    previewStyle = uiState.previewStyle,
                    soundEnabled = uiState.settings.soundEnabled,
                    volume = uiState.settings.volume,
                    touchEnabled = uiState.settings.touchEnabled,
                    reloadToken = uiState.previewReloadToken,
                    showDefaultSystemChrome = true,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(14.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissFullscreenPreview)
                        .testTag("close_fullscreen_preview_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit full mobile view",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (uiState.showApplySheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissApplySheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ApplyTargetSheetContent(
                selectedTarget = uiState.settings.wallpaperTarget,
                onSelectTarget = onApplyForTarget
            )
        }
    }

    if (uiState.showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissSettings,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WallpaperSettingsSheetContent(
                settings = uiState.settings,
                lockScreenWallpaper = uiState.lockScreenWallpaper,
                lockScreenTheme = uiState.lockScreenTheme,
                onLockScreenChangeEnabled = onLockScreenChangeEnabled,
                onUploadLockHtml = onUploadLockHtml,
                onUploadLockVideo = onUploadLockVideo,
                onUploadLockGif = onUploadLockGif,
                onUploadLockTheme = onUploadLockTheme,
                onClearLockScreenCustom = onClearLockScreenCustom,
                onApplyLockScreenNow = onApplyLockScreenNow,
                onSoundEnabledChange = onToggleSound,
                onVolumeChange = onVolumeChange,
                onMuteOtherMediaChange = onMuteOtherMediaChange,
                onTouchEnabledChange = onTouchEnabledChange,
                onThemeTransparentChange = onThemeTransparentChange,
                onRenderModeChange = onRenderModeChange
            )
        }
    }
}

@Composable
private fun ApplyTargetSheetContent(
    selectedTarget: WallpaperTarget,
    onSelectTarget: (WallpaperTarget) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.apply_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(2.dp))

        ApplyTargetOptionRow(
            title = stringResource(R.string.apply_home_only),
            subtitle = stringResource(R.string.apply_home_only_desc),
            icon = Icons.Default.Home,
            accent = CobaltAccent,
            selected = selectedTarget == WallpaperTarget.HOME_ONLY,
            onClick = { onSelectTarget(WallpaperTarget.HOME_ONLY) },
            testTag = "apply_option_home_only"
        )

        ApplyTargetOptionRow(
            title = stringResource(R.string.apply_lock_only),
            subtitle = stringResource(R.string.apply_lock_only_desc),
            icon = Icons.Default.Lock,
            accent = VioletVideo,
            selected = selectedTarget == WallpaperTarget.LOCK_ONLY,
            onClick = { onSelectTarget(WallpaperTarget.LOCK_ONLY) },
            testTag = "apply_option_lock_only"
        )

        ApplyTargetOptionRow(
            title = stringResource(R.string.apply_both),
            subtitle = stringResource(R.string.apply_both_desc),
            icon = Icons.Default.Smartphone,
            accent = EmeraldLive,
            selected = selectedTarget == WallpaperTarget.BOTH,
            onClick = { onSelectTarget(WallpaperTarget.BOTH) },
            testTag = "apply_option_both"
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.apply_sheet_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApplyTargetOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun UploadTileButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val containerBg by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.outline,
        label = "tile_border"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

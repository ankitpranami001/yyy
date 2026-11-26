package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RenderEngineMode
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSettings
import com.example.data.WallpaperSource
import com.example.ui.MainUiState
import com.example.ui.PhonePreviewStyle
import com.example.ui.components.PhonePreviewMockup
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CoralGif
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.VioletVideo
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: MainUiState,
    repository: WallpaperRepository,
    onUploadHtml: () -> Unit,
    onUploadVideo: () -> Unit,
    onUploadGif: () -> Unit,
    onSetHomeLockWallpaper: () -> Unit,
    onPreviewStyleChange: (PhonePreviewStyle) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteOtherMediaChange: (Boolean) -> Unit,
    onTouchEnabledChange: (Boolean) -> Unit,
    onRenderModeChange: (RenderEngineMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSource = uiState.activeWallpaper?.source

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
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
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (uiState.isServiceActiveOnHomeScreen) {
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
                                    text = stringResource(R.string.status_live_active),
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

            // ONLY 3 Upload Buttons in a single clean row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Centered Android Mobile Live Preview Mockup
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PhonePreviewMockup(
                    activeWallpaper = uiState.activeWallpaper,
                    repository = repository,
                    previewStyle = uiState.previewStyle,
                    soundEnabled = uiState.settings.soundEnabled,
                    volume = uiState.settings.volume,
                    touchEnabled = uiState.settings.touchEnabled,
                    reloadToken = uiState.previewReloadToken,
                    onPreviewStyleChange = onPreviewStyleChange,
                    onToggleSound = onToggleSound,
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
                                text = "Loading wallpaper...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Set Home & Lock Screen primary button
            Button(
                onClick = onSetHomeLockWallpaper,
                enabled = !uiState.isImporting,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
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
    }

    if (uiState.showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissSettings,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WallpaperSettingsSheetContent(
                settings = uiState.settings,
                onSoundEnabledChange = onToggleSound,
                onVolumeChange = onVolumeChange,
                onMuteOtherMediaChange = onMuteOtherMediaChange,
                onTouchEnabledChange = onTouchEnabledChange,
                onRenderModeChange = onRenderModeChange
            )
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
        shape = RoundedCornerShape(18.dp),
        color = containerBg,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WallpaperSettingsSheetContent(
    settings: WallpaperSettings,
    onSoundEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteOtherMediaChange: (Boolean) -> Unit,
    onTouchEnabledChange: (Boolean) -> Unit,
    onRenderModeChange: (RenderEngineMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        SettingSwitchRow(
            title = stringResource(R.string.controls_sound_enabled),
            subtitle = stringResource(R.string.controls_sound_desc),
            checked = settings.soundEnabled,
            onCheckedChange = onSoundEnabledChange,
            testTag = "sheet_sound_switch"
        )

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.controls_volume),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(settings.volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.volume,
                onValueChange = onVolumeChange,
                enabled = settings.soundEnabled,
                modifier = Modifier.testTag("sheet_volume_slider")
            )
        }

        SettingSwitchRow(
            title = stringResource(R.string.controls_mute_other_media),
            subtitle = stringResource(R.string.controls_mute_other_media_desc),
            checked = settings.muteDuringOtherMedia,
            onCheckedChange = onMuteOtherMediaChange,
            testTag = "sheet_auto_mute_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingSwitchRow(
            title = stringResource(R.string.controls_touch_enabled),
            subtitle = stringResource(R.string.controls_touch_desc),
            checked = settings.touchEnabled,
            onCheckedChange = onTouchEnabledChange,
            testTag = "sheet_touch_switch"
        )

        Text(
            text = stringResource(R.string.controls_render_mode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = settings.renderMode == RenderEngineMode.AUTO_HARDWARE,
                onClick = { onRenderModeChange(RenderEngineMode.AUTO_HARDWARE) },
                modifier = Modifier.testTag("sheet_render_hw")
            )
            Text(
                text = stringResource(R.string.controls_render_hardware),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = settings.renderMode == RenderEngineMode.COMPATIBILITY_CANVAS,
                onClick = { onRenderModeChange(RenderEngineMode.COMPATIBILITY_CANVAS) },
                modifier = Modifier.testTag("sheet_render_compat")
            )
            Text(
                text = stringResource(R.string.controls_render_compat),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

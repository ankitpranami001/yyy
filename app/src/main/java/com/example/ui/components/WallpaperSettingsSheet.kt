package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HtmlThemeItem
import com.example.data.RenderEngineMode
import com.example.data.WallpaperItem
import com.example.data.WallpaperSettings
import com.example.data.WallpaperTarget
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CoralGif
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.VioletVideo
import kotlin.math.roundToInt

@Composable
fun WallpaperSettingsSheetContent(
    settings: WallpaperSettings,
    lockScreenWallpaper: WallpaperItem?,
    lockScreenTheme: HtmlThemeItem?,
    onLockScreenChangeEnabled: (Boolean) -> Unit,
    onUploadLockHtml: () -> Unit,
    onUploadLockVideo: () -> Unit,
    onUploadLockGif: () -> Unit,
    onUploadLockTheme: () -> Unit,
    onClearLockScreenCustom: () -> Unit,
    onApplyLockScreenNow: () -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onMuteOtherMediaChange: (Boolean) -> Unit,
    onTouchEnabledChange: (Boolean) -> Unit,
    onThemeTransparentChange: (Boolean) -> Unit,
    onRenderModeChange: (RenderEngineMode) -> Unit
) {
    val lockScreenEnabled = settings.wallpaperTarget != WallpaperTarget.HOME_ONLY
    val hasCustomLockMedia = lockScreenWallpaper != null || lockScreenTheme != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
            title = stringResource(R.string.controls_theme_wallpaper_source),
            subtitle = stringResource(
                if (settings.themeTransparentBackground) {
                    R.string.controls_theme_wallpaper_uploaded_on
                } else {
                    R.string.controls_theme_wallpaper_theme_off
                }
            ),
            checked = settings.themeTransparentBackground,
            onCheckedChange = onThemeTransparentChange,
            testTag = "sheet_theme_transparent_switch"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Lock Screen Change Section
        SettingSwitchRow(
            title = stringResource(R.string.controls_lock_screen_change),
            subtitle = stringResource(
                if (lockScreenEnabled) {
                    R.string.controls_lock_screen_on
                } else {
                    R.string.controls_lock_screen_off
                }
            ),
            checked = lockScreenEnabled,
            onCheckedChange = onLockScreenChangeEnabled,
            testTag = "sheet_lock_screen_switch"
        )

        if (lockScreenEnabled) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_lock_screen_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val customSummary = buildList {
                            lockScreenWallpaper?.let { add(it.name) }
                            lockScreenTheme?.let { add("Theme: ${it.name}") }
                        }.joinToString(" + ")

                        Text(
                            text = if (hasCustomLockMedia) {
                                "${stringResource(R.string.controls_lock_screen_custom_prefix)} $customSummary"
                            } else {
                                stringResource(R.string.controls_lock_screen_same_as_home)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (hasCustomLockMedia) {
                            TextButton(
                                onClick = onClearLockScreenCustom,
                                modifier = Modifier.testTag("sheet_lock_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.controls_lock_screen_reset),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LockUploadChip(
                            label = stringResource(R.string.btn_upload_html),
                            icon = Icons.Default.Code,
                            accent = CobaltAccent,
                            onClick = onUploadLockHtml,
                            testTag = "sheet_lock_upload_html",
                            modifier = Modifier.weight(1f)
                        )
                        LockUploadChip(
                            label = stringResource(R.string.btn_upload_video),
                            icon = Icons.Default.Videocam,
                            accent = VioletVideo,
                            onClick = onUploadLockVideo,
                            testTag = "sheet_lock_upload_video",
                            modifier = Modifier.weight(1f)
                        )
                        LockUploadChip(
                            label = stringResource(R.string.btn_upload_gif),
                            icon = Icons.Default.GifBox,
                            accent = CoralGif,
                            onClick = onUploadLockGif,
                            testTag = "sheet_lock_upload_gif",
                            modifier = Modifier.weight(1f)
                        )
                        LockUploadChip(
                            label = stringResource(R.string.btn_upload_theme),
                            icon = Icons.Default.Palette,
                            accent = EmeraldLive,
                            onClick = onUploadLockTheme,
                            testTag = "sheet_lock_upload_theme",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FilledTonalButton(
                        onClick = onApplyLockScreenNow,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = VioletVideo.copy(alpha = 0.14f),
                            contentColor = VioletVideo
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sheet_apply_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.controls_lock_screen_apply),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                selected = settings.renderMode == RenderEngineMode.COMPATIBILITY_CANVAS,
                onClick = { onRenderModeChange(RenderEngineMode.COMPATIBILITY_CANVAS) },
                modifier = Modifier.testTag("sheet_render_compat")
            )
            Text(
                text = stringResource(R.string.controls_render_compat),
                style = MaterialTheme.typography.bodyMedium
            )
        }
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
    }
}

@Composable
private fun LockUploadChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Lock Screen $label",
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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

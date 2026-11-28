package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.HtmlThemeItem
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSource
import com.example.ui.PhonePreviewStyle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldLive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dual-layer live renderer:
 * - Bottom layer: Active Live Wallpaper (HTML / ZIP / Video / GIF / Builtin Aurora)
 * - Top layer: Uploaded HTML Theme (transparent overlay changing the entire mobile view),
 *   or default Android Lock/Home overlay when no theme is active.
 */
@Composable
fun DualLayerWallpaperCanvas(
    activeWallpaper: WallpaperItem?,
    activeTheme: HtmlThemeItem?,
    themeEnabled: Boolean,
    themeTransparent: Boolean,
    repository: WallpaperRepository,
    previewStyle: PhonePreviewStyle,
    soundEnabled: Boolean,
    volume: Float,
    touchEnabled: Boolean,
    reloadToken: Long,
    showDefaultSystemChrome: Boolean,
    modifier: Modifier = Modifier
) {
    val currentTime = remember { SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date()) }
    val currentDate = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }
    val showHtmlTheme = activeTheme != null && themeEnabled
    val viewModeStr = if (previewStyle == PhonePreviewStyle.HOME_SCREEN) "home" else "lock"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060912))
    ) {
        // LAYER 1: Live Wallpaper Background (shown when no theme is active OR "Use Uploaded Wallpaper" is ON)
        if (activeWallpaper != null && (!showHtmlTheme || themeTransparent)) {
            key("wp", activeWallpaper.id, reloadToken) {
                when (activeWallpaper.source) {
                    WallpaperSource.UPLOADED_VIDEO -> {
                        val file = repository.getMediaFile(activeWallpaper)
                        if (file != null) {
                            NativeVideoPreview(
                                videoFile = file,
                                soundEnabled = soundEnabled,
                                volume = volume,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            BuiltinShowcasePreview(
                                wallpaperId = activeWallpaper.id,
                                touchEnabled = touchEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    WallpaperSource.UPLOADED_GIF -> {
                        val file = repository.getMediaFile(activeWallpaper)
                        val url = repository.resolveWallpaperUrl(activeWallpaper)
                        if (file != null) {
                            NativeGifPreview(
                                gifFile = file,
                                fallbackUrl = url,
                                repository = repository,
                                reloadKey = Pair(activeWallpaper.id, reloadToken),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            BuiltinShowcasePreview(
                                wallpaperId = activeWallpaper.id,
                                touchEnabled = touchEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    WallpaperSource.UPLOADED_HTML,
                    WallpaperSource.UPLOADED_ZIP -> {
                        val url = repository.resolveWallpaperUrl(activeWallpaper)
                        LiveWebPreview(
                            url = url,
                            repository = repository,
                            soundEnabled = soundEnabled,
                            volume = volume,
                            touchEnabled = touchEnabled && !showHtmlTheme,
                            reloadKey = Pair(activeWallpaper.id, reloadToken),
                            preferSoftwareLayer = true,
                            transparentBackground = false,
                            viewMode = viewModeStr,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    WallpaperSource.BUILTIN -> {
                        BuiltinShowcasePreview(
                            wallpaperId = activeWallpaper.id,
                            touchEnabled = touchEnabled && !showHtmlTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // LAYER 2: Uploaded HTML Theme (replaces default Android view while keeping live wallpaper visible!)
        if (showHtmlTheme) {
            key("theme", activeTheme.id, themeTransparent, reloadToken) {
                val themeUrl = repository.resolveThemeUrl(activeTheme)
                LiveWebPreview(
                    url = themeUrl,
                    repository = repository,
                    soundEnabled = soundEnabled,
                    volume = volume,
                    touchEnabled = touchEnabled,
                    reloadKey = Triple(activeTheme.id, themeTransparent, reloadToken),
                    preferSoftwareLayer = true,
                    transparentBackground = themeTransparent,
                    viewMode = viewModeStr,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("active_html_theme_overlay")
                )
            }
        } else if (showDefaultSystemChrome) {
            // Default Android Status Bar + Lock/Home Overlay when no custom HTML Theme is active
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0D12))
                        .border(0.8.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(11.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (previewStyle == PhonePreviewStyle.LOCK_SCREEN) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = currentTime,
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    )
                    Text(
                        text = currentDate,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 46.dp, start = 18.dp, end = 18.dp)
                        .fillMaxWidth()
                        .height(30.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Search apps...",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 10.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp, start = 18.dp, end = 18.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        Icons.Default.Call,
                        Icons.AutoMirrored.Filled.Message,
                        Icons.Default.Public,
                        Icons.Default.CameraAlt
                    ).forEach { icon ->
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            border = BorderStroke(0.7.dp, Color.White.copy(alpha = 0.28f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 7.dp)
                    .width(64.dp)
                    .height(3.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.72f))
            )
        }
    }
}

@Composable
fun PhonePreviewMockup(
    activeWallpaper: WallpaperItem?,
    activeTheme: HtmlThemeItem?,
    themeEnabled: Boolean,
    themeTransparent: Boolean,
    repository: WallpaperRepository,
    previewStyle: PhonePreviewStyle,
    soundEnabled: Boolean,
    volume: Float,
    touchEnabled: Boolean,
    reloadToken: Long,
    onPreviewStyleChange: (PhonePreviewStyle) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleThemeOverlay: () -> Unit,
    onClearTheme: () -> Unit,
    onOpenFullscreenPreview: () -> Unit,
    isCustomLockPreview: Boolean = false,
    lockScreenChangeDisabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val phoneRatio = 9f / 18.8f
            val maxPhoneHeight = maxHeight.coerceAtMost(540.dp)
            val phoneWidth = (maxPhoneHeight * phoneRatio).coerceAtMost(maxWidth * 0.84f)
            val phoneHeight = (phoneWidth / phoneRatio).coerceAtMost(maxPhoneHeight)

            Box(
                modifier = Modifier
                    .size(width = phoneWidth, height = phoneHeight)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(36.dp),
                        ambientColor = Color.Black.copy(alpha = 0.24f),
                        spotColor = Color.Black.copy(alpha = 0.28f)
                    )
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xFF141820))
                    .border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(36.dp)
                    )
                    .padding(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(29.dp))
                ) {
                    DualLayerWallpaperCanvas(
                        activeWallpaper = activeWallpaper,
                        activeTheme = activeTheme,
                        themeEnabled = themeEnabled,
                        themeTransparent = themeTransparent,
                        repository = repository,
                        previewStyle = previewStyle,
                        soundEnabled = soundEnabled,
                        volume = volume,
                        touchEnabled = touchEnabled,
                        reloadToken = reloadToken,
                        showDefaultSystemChrome = true,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (activeWallpaper != null && activeWallpaper.source != WallpaperSource.UPLOADED_GIF) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.52f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 10.dp,
                                    bottom = if (previewStyle == PhonePreviewStyle.HOME_SCREEN && !(activeTheme != null && themeEnabled)) 66.dp else 20.dp
                                )
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onToggleSound(!soundEnabled) }
                                .testTag("preview_sound_toggle")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (soundEnabled) {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    },
                                    contentDescription = if (soundEnabled) "Mute preview" else "Unmute preview",
                                    tint = if (soundEnabled) CyanAccent else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Lock / Home / Full View + Theme Toggle pills
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreviewStylePill(
                label = stringResource(R.string.preview_mode_lock),
                icon = Icons.Default.Lock,
                selected = previewStyle == PhonePreviewStyle.LOCK_SCREEN,
                onClick = { onPreviewStyleChange(PhonePreviewStyle.LOCK_SCREEN) },
                testTag = "pill_preview_lock"
            )
            PreviewStylePill(
                label = stringResource(R.string.preview_mode_home),
                icon = Icons.Default.Home,
                selected = previewStyle == PhonePreviewStyle.HOME_SCREEN,
                onClick = { onPreviewStyleChange(PhonePreviewStyle.HOME_SCREEN) },
                testTag = "pill_preview_home"
            )
            PreviewStylePill(
                label = stringResource(R.string.preview_full_view),
                icon = Icons.Default.Fullscreen,
                selected = false,
                onClick = onOpenFullscreenPreview,
                testTag = "fullscreen_preview_button"
            )

            if (activeTheme != null) {
                Surface(
                    shape = CircleShape,
                    color = if (themeEnabled) EmeraldLive.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (themeEnabled) EmeraldLive else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable(onClick = onToggleThemeOverlay)
                                .testTag("theme_toggle_chip")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = if (themeEnabled) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = stringResource(
                                    if (themeEnabled) R.string.preview_theme_on else R.string.preview_theme_off
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (themeEnabled) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove HTML theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onClearTheme)
                                .testTag("clear_theme_button")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        val wpLabel = if (activeWallpaper == null || activeWallpaper.isBuiltIn) {
            null
        } else {
            val typeLabel = when (activeWallpaper.source) {
                WallpaperSource.UPLOADED_HTML -> "HTML"
                WallpaperSource.UPLOADED_ZIP -> "ZIP"
                WallpaperSource.UPLOADED_VIDEO -> "VIDEO"
                WallpaperSource.UPLOADED_GIF -> "GIF"
                WallpaperSource.BUILTIN -> "DEMO"
            }
            "$typeLabel • ${activeWallpaper.name}"
        }
        val themeLabel = if (activeTheme != null && themeEnabled) {
            "THEME • ${activeTheme.name}"
        } else {
            null
        }
        val baseCaption = when {
            themeLabel != null && !themeTransparent -> "$themeLabel • Theme Wallpaper"
            wpLabel != null && themeLabel != null -> "$wpLabel  +  $themeLabel"
            themeLabel != null -> "$themeLabel  +  Aurora Wallpaper"
            wpLabel != null -> wpLabel
            else -> stringResource(R.string.preview_empty_hint)
        }
        val caption = when {
            lockScreenChangeDisabled -> "Lock Screen OFF (Home Screen Only)"
            isCustomLockPreview -> "LOCK • $baseCaption"
            else -> baseCaption
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewStylePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = bg,
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

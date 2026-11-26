package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
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
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSource
import com.example.ui.PhonePreviewStyle
import com.example.ui.theme.CyanAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhonePreviewMockup(
    activeWallpaper: WallpaperItem?,
    repository: WallpaperRepository,
    previewStyle: PhonePreviewStyle,
    soundEnabled: Boolean,
    volume: Float,
    touchEnabled: Boolean,
    reloadToken: Long,
    onPreviewStyleChange: (PhonePreviewStyle) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTime = remember { SimpleDateFormat("hh:mm", Locale.getDefault()).format(Date()) }
    val currentDate = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Phone frame constrained dynamically to available height & width
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val phoneRatio = 9f / 18.8f
            val maxPhoneHeight = maxHeight.coerceAtMost(560.dp)
            val phoneWidth = (maxPhoneHeight * phoneRatio).coerceAtMost(maxWidth * 0.86f)
            val phoneHeight = (phoneWidth / phoneRatio).coerceAtMost(maxPhoneHeight)

            // Outer sleek Android hardware bezel
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
                // Inner Android display screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(29.dp))
                        .background(Color(0xFF060912))
                ) {
                    // Live Wallpaper Layer
                    if (activeWallpaper != null) {
                        key(activeWallpaper.id, reloadToken) {
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
                                        touchEnabled = touchEnabled,
                                        reloadKey = Pair(activeWallpaper.id, reloadToken),
                                        preferSoftwareLayer = true,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                WallpaperSource.BUILTIN -> {
                                    BuiltinShowcasePreview(
                                        wallpaperId = activeWallpaper.id,
                                        touchEnabled = touchEnabled,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Subtle top scrim for Android status bar legibility
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

                    // Android Status Bar + Punch-hole Camera
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
                        // Punch-hole camera
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

                    // Lock Screen OR Home Screen Authentic Android Overlay
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
                        // Clean Android Home Screen Search Pill & Bottom Dock
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

                        // Minimal bottom Android app dock
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 22.dp, start = 18.dp, end = 18.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(
                                Icons.Default.Call,
                                Icons.Default.Message,
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

                    // Sound quick toggle pill inside screen corner (for HTML / Video)
                    if (activeWallpaper != null && activeWallpaper.source != WallpaperSource.UPLOADED_GIF) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.52f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = 10.dp,
                                    bottom = if (previewStyle == PhonePreviewStyle.HOME_SCREEN) 66.dp else 24.dp
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

                    // Android gesture pill at very bottom
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

        Spacer(modifier = Modifier.height(12.dp))

        // Compact Lock / Home preview switcher + loaded file name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        }

        Spacer(modifier = Modifier.height(6.dp))

        val caption = if (activeWallpaper == null || activeWallpaper.isBuiltIn) {
            stringResource(R.string.preview_empty_hint)
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
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

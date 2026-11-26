package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSource
import com.example.ui.MainUiState
import com.example.ui.components.BuiltinShowcasePreview
import com.example.ui.components.LiveWebPreview
import com.example.ui.theme.CoralGlow
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.ObsidianVoid

@Composable
fun GalleryScreen(
    uiState: MainUiState,
    repository: WallpaperRepository,
    isPreviewActive: Boolean,
    onUploadClick: () -> Unit,
    onSetLiveWallpaperClick: () -> Unit,
    onSelectWallpaper: (WallpaperItem) -> Unit,
    onOpenFullscreen: (WallpaperItem) -> Unit,
    onEditInCodeLab: (WallpaperItem?) -> Unit,
    onDeleteWallpaper: (WallpaperItem) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val userWallpapers = remember(uiState.wallpapers) {
        uiState.wallpapers.filter { !it.isBuiltIn }
    }
    val builtInWallpapers = remember(uiState.wallpapers) {
        uiState.wallpapers.filter { it.isBuiltIn }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                StudioHeader(isServiceActive = uiState.isServiceActiveOnHomeScreen)
            }

            item(key = "hero_stage") {
                val active = uiState.activeWallpaper
                if (active != null) {
                    HeroLiveStageCard(
                        item = active,
                        repository = repository,
                        isPreviewActive = isPreviewActive,
                        soundEnabled = uiState.settings.soundEnabled,
                        volume = uiState.settings.volume,
                        touchEnabled = uiState.settings.touchEnabled,
                        reloadToken = uiState.previewReloadToken,
                        onToggleSound = onToggleSound,
                        onOpenFullscreen = { onOpenFullscreen(active) }
                    )
                }
            }

            item(key = "primary_actions") {
                PrimaryActionCluster(
                    isServiceActive = uiState.isServiceActiveOnHomeScreen,
                    isImporting = uiState.isImporting,
                    onSetLiveWallpaperClick = onSetLiveWallpaperClick,
                    onUploadClick = onUploadClick
                )
            }

            item(key = "uploads_header") {
                SectionHeaderRow(
                    title = stringResource(R.string.section_my_uploads),
                    count = userWallpapers.size,
                    actionLabel = stringResource(R.string.action_new_code),
                    onActionClick = { onEditInCodeLab(null) }
                )
            }

            if (userWallpapers.isEmpty()) {
                item(key = "empty_uploads") {
                    EmptyUploadsCard(
                        onUploadClick = onUploadClick,
                        onWriteCodeClick = { onEditInCodeLab(null) }
                    )
                }
            } else {
                items(userWallpapers, key = { it.id }) { item ->
                    WallpaperListCard(
                        item = item,
                        isSelected = item.id == uiState.activeWallpaper?.id,
                        onSelect = { onSelectWallpaper(item) },
                        onFullscreen = { onOpenFullscreen(item) },
                        onEditCode = { onEditInCodeLab(item) },
                        onDelete = { onDeleteWallpaper(item) }
                    )
                }
            }

            item(key = "builtin_header") {
                SectionHeaderRow(
                    title = stringResource(R.string.section_builtin_showcase),
                    count = builtInWallpapers.size
                )
            }

            items(builtInWallpapers, key = { it.id }) { item ->
                WallpaperListCard(
                    item = item,
                    isSelected = item.id == uiState.activeWallpaper?.id,
                    onSelect = { onSelectWallpaper(item) },
                    onFullscreen = { onOpenFullscreen(item) },
                    onEditCode = { onEditInCodeLab(item) },
                    onDelete = null
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StudioHeader(isServiceActive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "HTML5 Animation & Sound Engine",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val pillBg = if (isServiceActive) {
            EmeraldLive.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val pillTextColor = if (isServiceActive) {
            EmeraldLive
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            shape = CircleShape,
            color = pillBg,
            border = BorderStroke(
                1.dp,
                if (isServiceActive) EmeraldLive.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isServiceActive) EmeraldLive else CyanElectric)
                )
                Text(
                    text = if (isServiceActive) {
                        stringResource(R.string.status_active_wallpaper)
                    } else {
                        stringResource(R.string.status_not_set_wallpaper)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = pillTextColor
                )
            }
        }
    }
}

@Composable
private fun HeroLiveStageCard(
    item: WallpaperItem,
    repository: WallpaperRepository,
    isPreviewActive: Boolean,
    soundEnabled: Boolean,
    volume: Float,
    touchEnabled: Boolean,
    reloadToken: Long,
    onToggleSound: (Boolean) -> Unit,
    onOpenFullscreen: () -> Unit
) {
    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                CyanElectric.copy(alpha = 0.7f),
                CoralGlow.copy(alpha = 0.55f),
                CyanElectric.copy(alpha = 0.35f)
            )
        )
    }
    val url = remember(item.id, item.entryFile) { repository.resolveWallpaperUrl(item) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(296.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.5.dp, borderBrush, RoundedCornerShape(26.dp))
            .background(ObsidianVoid)
    ) {
        if (isPreviewActive) {
            if (item.isBuiltIn) {
                BuiltinShowcasePreview(
                    wallpaperId = item.id,
                    touchEnabled = touchEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LiveWebPreview(
                    url = url,
                    repository = repository,
                    soundEnabled = soundEnabled,
                    volume = volume,
                    touchEnabled = touchEnabled,
                    reloadKey = Pair(item.id, reloadToken),
                    preferSoftwareLayer = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Floating quick controls top-right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ObsidianVoid.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                IconButton(
                    onClick = { onToggleSound(!soundEnabled) },
                    modifier = Modifier.testTag("hero_sound_toggle")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = if (soundEnabled) "Mute preview sound" else "Enable preview sound",
                        tint = if (soundEnabled) CyanElectric else Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = ObsidianVoid.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                IconButton(
                    onClick = onOpenFullscreen,
                    modifier = Modifier.testTag("hero_fullscreen_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = stringResource(R.string.action_preview_fullscreen),
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom metadata gradient scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ObsidianVoid.copy(alpha = 0.88f),
                            ObsidianVoid.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceBadge(source = item.source)
                        Text(
                            text = formatFileSize(item.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Interactive live preview • Tap canvas to test",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionCluster(
    isServiceActive: Boolean,
    isImporting: Boolean,
    onSetLiveWallpaperClick: () -> Unit,
    onUploadClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onSetLiveWallpaperClick,
            modifier = Modifier
                .weight(1.15f)
                .height(54.dp)
                .testTag("set_live_wallpaper_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Wallpaper,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isServiceActive) {
                    stringResource(R.string.action_update_wallpaper)
                } else {
                    stringResource(R.string.action_set_wallpaper)
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        FilledTonalButton(
            onClick = onUploadClick,
            enabled = !isImporting,
            modifier = Modifier
                .weight(0.95f)
                .height(54.dp)
                .testTag("upload_html_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.action_upload_html),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    count: Int,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (actionLabel != null && onActionClick != null) {
            OutlinedButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.testTag("section_code_new_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyUploadsCard(
    onUploadClick: () -> Unit,
    onWriteCodeClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.empty_uploads_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empty_uploads_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onUploadClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("empty_upload_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select HTML / ZIP")
                }
                OutlinedButton(
                    onClick = onWriteCodeClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("empty_write_code_button")
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste HTML")
                }
            }
        }
    }
}

@Composable
private fun WallpaperListCard(
    item: WallpaperItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onFullscreen: () -> Unit,
    onEditCode: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val accentColor = remember(item.accentHex) { parseAccentHex(item.accentHex) }
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "card_border"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "card_container"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect)
            .testTag("wallpaper_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing color emblem
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.28f),
                                accentColor.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Code,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    SourceBadge(source = item.source)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.description.ifBlank { item.entryFile },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onFullscreen,
                    modifier = Modifier.testTag("preview_btn_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen preview ${item.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onEditCode,
                    modifier = Modifier.testTag("edit_btn_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Edit HTML of ${item.name}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_btn_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ${item.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(source: WallpaperSource) {
    val (label, bg, fg) = when (source) {
        WallpaperSource.BUILTIN -> Triple("SHOWCASE", CyanElectric.copy(alpha = 0.16f), CyanElectric)
        WallpaperSource.UPLOADED_HTML -> Triple("HTML", CoralGlow.copy(alpha = 0.18f), CoralGlow)
        WallpaperSource.UPLOADED_ZIP -> Triple("ZIP", EmeraldLive.copy(alpha = 0.18f), EmeraldLive)
        WallpaperSource.CUSTOM_CODE -> Triple("CODE", MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), MaterialTheme.colorScheme.tertiary)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun parseAccentHex(hex: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(CyanElectric)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

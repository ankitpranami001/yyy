package com.example.ui

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HtmlThemeItem
import com.example.data.RenderEngineMode
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSettings
import com.example.data.WallpaperTarget
import com.example.wallpaper.HtmlLiveWallpaperService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PhonePreviewStyle {
    LOCK_SCREEN,
    HOME_SCREEN
}

data class MainUiState(
    val activeWallpaper: WallpaperItem? = null,
    val activeTheme: HtmlThemeItem? = null,
    val lockScreenWallpaper: WallpaperItem? = null,
    val lockScreenTheme: HtmlThemeItem? = null,
    val settings: WallpaperSettings = WallpaperSettings(),
    val isServiceActiveOnHomeScreen: Boolean = false,
    val isImporting: Boolean = false,
    val previewStyle: PhonePreviewStyle = PhonePreviewStyle.LOCK_SCREEN,
    val showApplySheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showFullscreenPreview: Boolean = false,
    val statusMessage: String? = null,
    val previewReloadToken: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WallpaperRepository(application.applicationContext)

    private val isServiceActive = MutableStateFlow(checkIsOurWallpaperActive())
    private val isImporting = MutableStateFlow(false)
    private val previewStyle = MutableStateFlow(PhonePreviewStyle.LOCK_SCREEN)
    private val showApplySheet = MutableStateFlow(false)
    private val showSettingsSheet = MutableStateFlow(false)
    private val showFullscreenPreview = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val previewReloadToken = MutableStateFlow(0L)

    val uiState: StateFlow<MainUiState> = combine(
        repository.wallpapers,
        repository.themes,
        repository.settings,
        isServiceActive,
        isImporting,
        previewStyle,
        showApplySheet,
        showSettingsSheet,
        showFullscreenPreview,
        statusMessage,
        previewReloadToken
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<WallpaperItem>
        @Suppress("UNCHECKED_CAST")
        val themes = values[1] as List<HtmlThemeItem>
        val settings = values[2] as WallpaperSettings
        val activeOnHome = values[3] as Boolean
        val importing = values[4] as Boolean
        val style = values[5] as PhonePreviewStyle
        val applySheet = values[6] as Boolean
        val settingsSheet = values[7] as Boolean
        val fullscreen = values[8] as Boolean
        val message = values[9] as String?
        val token = values[10] as Long

        val active = list.firstOrNull { it.id == settings.activeWallpaperId } ?: list.firstOrNull()
        val theme = settings.activeThemeId?.let { id -> themes.firstOrNull { it.id == id } }
        val lockWp = settings.lockScreenWallpaperId?.let { id -> list.firstOrNull { it.id == id } }
        val lockTheme = settings.lockScreenThemeId?.let { id -> themes.firstOrNull { it.id == id } }
        MainUiState(
            activeWallpaper = active,
            activeTheme = theme,
            lockScreenWallpaper = lockWp,
            lockScreenTheme = lockTheme,
            settings = settings,
            isServiceActiveOnHomeScreen = activeOnHome,
            isImporting = importing,
            previewStyle = style,
            showApplySheet = applySheet,
            showSettingsSheet = settingsSheet,
            showFullscreenPreview = fullscreen,
            statusMessage = message,
            previewReloadToken = token
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            activeWallpaper = repository.getActiveWallpaper(),
            activeTheme = repository.getSelectedThemeRaw(),
            lockScreenWallpaper = repository.getLockScreenWallpaperRaw(),
            lockScreenTheme = repository.getLockScreenThemeRaw(),
            settings = repository.settings.value,
            isServiceActiveOnHomeScreen = checkIsOurWallpaperActive()
        )
    )

    fun refreshWallpaperServiceStatus() {
        isServiceActive.value = checkIsOurWallpaperActive()
    }

    fun setPreviewStyle(style: PhonePreviewStyle) {
        previewStyle.value = style
    }

    fun setApplySheetVisible(visible: Boolean) {
        showApplySheet.value = visible
    }

    fun setSettingsSheetVisible(visible: Boolean) {
        showSettingsSheet.value = visible
    }

    fun setFullscreenPreviewVisible(visible: Boolean) {
        showFullscreenPreview.value = visible
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun showMessage(msg: String) {
        statusMessage.value = msg
    }

    fun selectWallpaperTarget(target: WallpaperTarget) {
        repository.updateSettings { it.copy(wallpaperTarget = target) }
        previewStyle.value = when (target) {
            WallpaperTarget.HOME_ONLY -> PhonePreviewStyle.HOME_SCREEN
            WallpaperTarget.LOCK_ONLY -> PhonePreviewStyle.LOCK_SCREEN
            WallpaperTarget.BOTH -> previewStyle.value
        }
        showApplySheet.value = false
    }

    fun setLockScreenChangeEnabled(enabled: Boolean) {
        repository.updateSettings { current ->
            val nextTarget = if (enabled) {
                if (current.wallpaperTarget == WallpaperTarget.HOME_ONLY) {
                    WallpaperTarget.BOTH
                } else {
                    current.wallpaperTarget
                }
            } else {
                WallpaperTarget.HOME_ONLY
            }
            current.copy(wallpaperTarget = nextTarget)
        }
        previewReloadToken.value = System.currentTimeMillis()
    }

    fun clearLockScreenCustom() {
        repository.clearLockScreenCustomization()
        previewReloadToken.value = System.currentTimeMillis()
        statusMessage.value = "Lock Screen now matches Home Screen"
    }

    fun importHtml(uri: Uri, forLockScreen: Boolean = false) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importHtmlOrZip(uri, forLockScreen = forLockScreen)
            isImporting.value = false
            handleImportResult(result, "HTML", forLockScreen)
        }
    }

    fun importVideo(uri: Uri, forLockScreen: Boolean = false) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importVideo(uri, forLockScreen = forLockScreen)
            isImporting.value = false
            handleImportResult(result, "Video", forLockScreen)
        }
    }

    fun importGif(uri: Uri, forLockScreen: Boolean = false) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importGif(uri, forLockScreen = forLockScreen)
            isImporting.value = false
            handleImportResult(result, "GIF", forLockScreen)
        }
    }

    fun importHtmlTheme(uri: Uri, forLockScreen: Boolean = false) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importHtmlTheme(uri, forLockScreen = forLockScreen)
            isImporting.value = false
            result.onSuccess { theme ->
                previewReloadToken.value = System.currentTimeMillis()
                if (forLockScreen) {
                    previewStyle.value = PhonePreviewStyle.LOCK_SCREEN
                    showSettingsSheet.value = false
                }
                val prefix = if (forLockScreen) "Lock Screen Theme" else "HTML Theme"
                statusMessage.value = if (isServiceActive.value) {
                    "$prefix active: \"${theme.name}\" (updated live)"
                } else {
                    "$prefix ready: \"${theme.name}\""
                }
            }.onFailure { err ->
                statusMessage.value = "Could not load HTML Theme: ${err.message ?: "Unsupported file"}"
            }
        }
    }

    fun toggleThemeEnabled() {
        repository.updateSettings { it.copy(themeEnabled = !it.themeEnabled) }
    }

    fun clearActiveTheme() {
        repository.clearActiveTheme()
        statusMessage.value = "Default mobile view restored"
    }

    fun setThemeTransparentBackground(transparent: Boolean) {
        repository.updateSettings { it.copy(themeTransparentBackground = transparent) }
        previewReloadToken.value = System.currentTimeMillis()
    }

    private fun handleImportResult(
        result: Result<WallpaperItem>,
        label: String,
        forLockScreen: Boolean = false
    ) {
        result.onSuccess { item ->
            previewReloadToken.value = System.currentTimeMillis()
            if (forLockScreen) {
                previewStyle.value = PhonePreviewStyle.LOCK_SCREEN
                showSettingsSheet.value = false
            }
            val prefix = if (forLockScreen) "Lock Screen $label" else label
            statusMessage.value = if (isServiceActive.value) {
                "$prefix loaded: \"${item.name}\" (updated live)"
            } else {
                "$prefix ready: \"${item.name}\""
            }
        }.onFailure { err ->
            statusMessage.value = "Could not load $label: ${err.message ?: "Unsupported file"}"
        }
    }

    fun toggleDarkTheme() {
        repository.updateSettings { it.copy(darkTheme = !it.darkTheme) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        repository.updateSettings { it.copy(soundEnabled = enabled) }
    }

    fun setVolume(volume: Float) {
        repository.updateSettings { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    fun setMuteDuringOtherMedia(enabled: Boolean) {
        repository.updateSettings { it.copy(muteDuringOtherMedia = enabled) }
    }

    fun setTouchEnabled(enabled: Boolean) {
        repository.updateSettings { it.copy(touchEnabled = enabled) }
    }

    fun setRenderMode(mode: RenderEngineMode) {
        repository.updateSettings { it.copy(renderMode = mode) }
    }

    private fun checkIsOurWallpaperActive(): Boolean {
        return runCatching {
            val wm = WallpaperManager.getInstance(getApplication())
            val info = wm.wallpaperInfo ?: return false
            val ours = ComponentName(getApplication(), HtmlLiveWallpaperService::class.java)
            info.component == ours
        }.getOrDefault(false)
    }

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}

package com.example.ui

import android.app.Application
import android.app.WallpaperManager
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RenderEngineMode
import com.example.data.WallpaperItem
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSettings
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
    val settings: WallpaperSettings = WallpaperSettings(),
    val isServiceActiveOnHomeScreen: Boolean = false,
    val isImporting: Boolean = false,
    val previewStyle: PhonePreviewStyle = PhonePreviewStyle.LOCK_SCREEN,
    val showSettingsSheet: Boolean = false,
    val statusMessage: String? = null,
    val previewReloadToken: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WallpaperRepository(application.applicationContext)

    private val isServiceActive = MutableStateFlow(checkIsOurWallpaperActive())
    private val isImporting = MutableStateFlow(false)
    private val previewStyle = MutableStateFlow(PhonePreviewStyle.LOCK_SCREEN)
    private val showSettingsSheet = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val previewReloadToken = MutableStateFlow(0L)

    val uiState: StateFlow<MainUiState> = combine(
        repository.wallpapers,
        repository.settings,
        isServiceActive,
        isImporting,
        previewStyle,
        showSettingsSheet,
        statusMessage,
        previewReloadToken
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<WallpaperItem>
        val settings = values[1] as WallpaperSettings
        val activeOnHome = values[2] as Boolean
        val importing = values[3] as Boolean
        val style = values[4] as PhonePreviewStyle
        val sheet = values[5] as Boolean
        val message = values[6] as String?
        val token = values[7] as Long

        val active = list.firstOrNull { it.id == settings.activeWallpaperId } ?: list.firstOrNull()
        MainUiState(
            activeWallpaper = active,
            settings = settings,
            isServiceActiveOnHomeScreen = activeOnHome,
            isImporting = importing,
            previewStyle = style,
            showSettingsSheet = sheet,
            statusMessage = message,
            previewReloadToken = token
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            activeWallpaper = repository.getActiveWallpaper(),
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

    fun setSettingsSheetVisible(visible: Boolean) {
        showSettingsSheet.value = visible
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun showMessage(msg: String) {
        statusMessage.value = msg
    }

    fun importHtml(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importHtmlOrZip(uri)
            isImporting.value = false
            handleImportResult(result, "HTML")
        }
    }

    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importVideo(uri)
            isImporting.value = false
            handleImportResult(result, "Video")
        }
    }

    fun importGif(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importGif(uri)
            isImporting.value = false
            handleImportResult(result, "GIF")
        }
    }

    private fun handleImportResult(result: Result<WallpaperItem>, label: String) {
        result.onSuccess { item ->
            previewReloadToken.value = System.currentTimeMillis()
            statusMessage.value = if (isServiceActive.value) {
                "$label loaded: \"${item.name}\" (live screen updated)"
            } else {
                "$label ready: \"${item.name}\""
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

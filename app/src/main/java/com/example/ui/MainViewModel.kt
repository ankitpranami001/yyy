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

enum class AppTab {
    GALLERY,
    CODE_LAB,
    CONTROLS
}

data class CodeLabState(
    val editingWallpaperId: String? = null,
    val name: String = "Neon Ripple Synth",
    val htmlCode: String = DEFAULT_STARTER_HTML,
    val showPreview: Boolean = false,
    val previewRevision: Int = 0
)

data class MainUiState(
    val wallpapers: List<WallpaperItem> = emptyList(),
    val settings: WallpaperSettings = WallpaperSettings(),
    val activeWallpaper: WallpaperItem? = null,
    val isServiceActiveOnHomeScreen: Boolean = false,
    val isImporting: Boolean = false,
    val selectedTab: AppTab = AppTab.GALLERY,
    val fullscreenPreviewItem: WallpaperItem? = null,
    val codeLab: CodeLabState = CodeLabState(),
    val statusBannerMessage: String? = null,
    val previewReloadToken: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WallpaperRepository(application.applicationContext)

    private val selectedTab = MutableStateFlow(AppTab.GALLERY)
    private val isServiceActive = MutableStateFlow(checkIsOurWallpaperActive())
    private val isImporting = MutableStateFlow(false)
    private val fullscreenItem = MutableStateFlow<WallpaperItem?>(null)
    private val codeLabState = MutableStateFlow(CodeLabState())
    private val statusMessage = MutableStateFlow<String?>(null)
    private val previewReloadToken = MutableStateFlow(0L)

    val uiState: StateFlow<MainUiState> = combine(
        repository.wallpapers,
        repository.settings,
        selectedTab,
        isServiceActive,
        isImporting,
        fullscreenItem,
        codeLabState,
        statusMessage,
        previewReloadToken
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<WallpaperItem>
        val settings = values[1] as WallpaperSettings
        val tab = values[2] as AppTab
        val activeOnHome = values[3] as Boolean
        val importing = values[4] as Boolean
        val fullscreen = values[5] as WallpaperItem?
        val codeLab = values[6] as CodeLabState
        val message = values[7] as String?
        val token = values[8] as Long

        val active = list.firstOrNull { it.id == settings.activeWallpaperId } ?: list.firstOrNull()
        MainUiState(
            wallpapers = list,
            settings = settings,
            activeWallpaper = active,
            isServiceActiveOnHomeScreen = activeOnHome,
            isImporting = importing,
            selectedTab = tab,
            fullscreenPreviewItem = fullscreen,
            codeLab = codeLab,
            statusBannerMessage = message,
            previewReloadToken = token
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            wallpapers = repository.wallpapers.value,
            settings = repository.settings.value,
            activeWallpaper = repository.getActiveWallpaper(),
            isServiceActiveOnHomeScreen = checkIsOurWallpaperActive()
        )
    )

    fun refreshWallpaperServiceStatus() {
        isServiceActive.value = checkIsOurWallpaperActive()
    }

    fun selectTab(tab: AppTab) {
        selectedTab.value = tab
    }

    fun selectWallpaper(item: WallpaperItem) {
        repository.selectWallpaper(item.id)
        if (isServiceActive.value) {
            statusMessage.value = "Home screen updated to \"${item.name}\""
        }
    }

    fun openFullscreenPreview(item: WallpaperItem) {
        fullscreenItem.value = item
    }

    fun closeFullscreenPreview() {
        fullscreenItem.value = null
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun showMessage(msg: String) {
        statusMessage.value = msg
    }

    fun importHtmlOrZip(uri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            val result = repository.importFromUri(uri)
            isImporting.value = false
            result.onSuccess { item ->
                previewReloadToken.value = System.currentTimeMillis()
                statusMessage.value = if (isServiceActive.value) {
                    "Imported \"${item.name}\" & updated home screen!"
                } else {
                    "Imported \"${item.name}\" — tap Set as Live Wallpaper!"
                }
            }.onFailure { err ->
                statusMessage.value = "Import failed: ${err.message ?: "Unsupported file"}"
            }
        }
    }

    fun deleteWallpaper(item: WallpaperItem) {
        if (item.isBuiltIn) return
        viewModelScope.launch {
            val deleted = repository.deleteWallpaper(item.id)
            if (deleted) {
                if (fullscreenItem.value?.id == item.id) {
                    fullscreenItem.value = null
                }
                if (codeLabState.value.editingWallpaperId == item.id) {
                    codeLabState.value = CodeLabState()
                }
                statusMessage.value = "Removed \"${item.name}\""
            }
        }
    }

    fun openInCodeLab(item: WallpaperItem?) {
        if (item == null) {
            codeLabState.value = CodeLabState(
                editingWallpaperId = null,
                name = "Neon Ripple Synth",
                htmlCode = DEFAULT_STARTER_HTML,
                showPreview = false,
                previewRevision = codeLabState.value.previewRevision + 1
            )
            selectedTab.value = AppTab.CODE_LAB
            return
        }
        viewModelScope.launch {
            val html = repository.readWallpaperHtml(item)
            codeLabState.value = CodeLabState(
                editingWallpaperId = if (item.isBuiltIn) null else item.id,
                name = if (item.isBuiltIn) "${item.name} Remix" else item.name,
                htmlCode = html.ifBlank { DEFAULT_STARTER_HTML },
                showPreview = false,
                previewRevision = codeLabState.value.previewRevision + 1
            )
            selectedTab.value = AppTab.CODE_LAB
        }
    }

    fun updateCodeLabName(name: String) {
        codeLabState.value = codeLabState.value.copy(name = name)
    }

    fun updateCodeLabHtml(code: String) {
        codeLabState.value = codeLabState.value.copy(htmlCode = code)
    }

    fun setCodeLabPreviewVisible(show: Boolean) {
        codeLabState.value = codeLabState.value.copy(
            showPreview = show,
            previewRevision = if (show) codeLabState.value.previewRevision + 1 else codeLabState.value.previewRevision
        )
    }

    fun loadStarterTemplate() {
        codeLabState.value = codeLabState.value.copy(
            editingWallpaperId = null,
            name = "Neon Ripple Synth",
            htmlCode = DEFAULT_STARTER_HTML,
            previewRevision = codeLabState.value.previewRevision + 1
        )
    }

    fun saveCodeLabWallpaper() {
        val current = codeLabState.value
        if (current.htmlCode.isBlank()) {
            statusMessage.value = "Please enter some HTML code first"
            return
        }
        viewModelScope.launch {
            val result = repository.saveCustomHtml(
                name = current.name,
                htmlContent = current.htmlCode,
                existingId = current.editingWallpaperId
            )
            result.onSuccess { saved ->
                codeLabState.value = current.copy(editingWallpaperId = saved.id)
                previewReloadToken.value = System.currentTimeMillis()
                statusMessage.value = "Saved \"${saved.name}\" & selected!"
                selectedTab.value = AppTab.GALLERY
            }.onFailure { err ->
                statusMessage.value = "Save failed: ${err.message}"
            }
        }
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

    fun setTargetFps(fps: Int) {
        repository.updateSettings { it.copy(targetFps = fps) }
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

private val DEFAULT_STARTER_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,user-scalable=no">
<style>
  html, body { margin: 0; height: 100%; overflow: hidden; background: #070B14; }
  canvas { display: block; width: 100%; height: 100%; }
</style>
</head>
<body>
<canvas id="cv"></canvas>
<script>
const cv = document.getElementById('cv');
const ctx = cv.getContext('2d');
let W, H, t = 0, ripples = [];
function resize() { W = cv.width = innerWidth; H = cv.height = innerHeight; }
resize(); addEventListener('resize', resize);

let ac;
function chime(x) {
  try {
    ac = ac || new (window.AudioContext || window.webkitAudioContext)();
    const o = ac.createOscillator(), g = ac.createGain();
    o.type = 'sine';
    o.frequency.value = 260 + (x / Math.max(1, W)) * 520;
    g.gain.setValueAtTime(0.12, ac.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, ac.currentTime + 0.9);
    o.connect(g); g.connect(ac.destination);
    o.start(); o.stop(ac.currentTime + 0.92);
  } catch (_) {}
}

addEventListener('pointerdown', e => {
  ripples.push({ x: e.clientX, y: e.clientY, r: 8, a: 1 });
  chime(e.clientX);
});

function loop() {
  t += 0.02;
  ctx.fillStyle = 'rgba(7,11,20,0.22)';
  ctx.fillRect(0, 0, W, H);
  for (let i = 0; i < 4; i++) {
    ctx.strokeStyle = i % 2 ? 'rgba(34,211,238,0.55)' : 'rgba(255,122,89,0.45)';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    for (let x = 0; x <= W; x += 14) {
      const y = H * 0.5 + Math.sin(x * 0.01 + t + i * 1.1) * (55 + i * 18);
      x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
  for (let i = ripples.length - 1; i >= 0; i--) {
    const p = ripples[i];
    p.r += 4; p.a *= 0.96;
    if (p.a < 0.02) { ripples.splice(i, 1); continue; }
    ctx.strokeStyle = 'rgba(34,211,238,' + p.a.toFixed(2) + ')';
    ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2); ctx.stroke();
  }
  requestAnimationFrame(loop);
}
loop();
</script>
</body>
</html>
""".trimIndent()

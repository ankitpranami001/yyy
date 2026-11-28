package com.example.wallpaper

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.data.RenderEngineMode
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSource
import com.example.data.WallpaperTarget
import android.app.WallpaperManager
import java.io.FileInputStream

class HtmlLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HtmlWallpaperEngine()

    private enum class EngineMode {
        NONE,
        DEDICATED_INACTIVE_MASK,
        VIDEO_DIRECT,
        VIDEO_THEME_PRESENTATION,
        GIF_CANVAS,
        WEB_PRESENTATION,
        WEB_CANVAS,
        NATIVE_FALLBACK_CANVAS
    }

    inner class HtmlWallpaperEngine : Engine() {

        private val mainHandler = Handler(Looper.getMainLooper())
        private lateinit var repository: WallpaperRepository
        private lateinit var audioManager: AudioManager
        private lateinit var keyguardManager: KeyguardManager

        private var engineMode: EngineMode = EngineMode.NONE
        private var virtualDisplay: VirtualDisplay? = null
        private var presentation: MaskedPresentation? = null
        private var webView: WebView? = null
        private var themeWebView: WebView? = null
        private var videoTextureSurface: Surface? = null
        private var mediaPlayer: MediaPlayer? = null
        private var gifRenderer: GifWallpaperRenderer? = null
        private var isVideoPrepared: Boolean = false

        private var currentSurface: Surface? = null
        private var surfaceWidth: Int = 0
        private var surfaceHeight: Int = 0
        private var currentActiveId: String? = null
        private var currentThemeId: String? = null
        private var currentThemeTransparent: Boolean = true
        private var currentRevision: Long = -1L
        private var rendererCrashCount: Int = 0
        private var isEngineVisible: Boolean = false
        private var otherAppPlayingAudio: Boolean = false
        private var lastFrameNanos: Long = 0L
        private var frameScheduled: Boolean = false
        private var lockReceiverRegistered: Boolean = false
        private var playbackCallback: Any? = null

        private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val unlockResyncRunnable = Runnable {
            if (isEngineVisible) {
                refreshRendererIfScreenContentChanged()
                syncActivePlaybackAndVisibility()
            }
        }

        private val lockStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                mainHandler.post {
                    refreshRendererIfScreenContentChanged()
                    syncActivePlaybackAndVisibility()
                    scheduleUnlockResyncs()
                }
            }
        }

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
            mainHandler.post {
                val holder = surfaceHolder
                if (holder != null && holder.surface != null && holder.surface.isValid &&
                    surfaceWidth > 0 && surfaceHeight > 0
                ) {
                    val forLock = isLockScreenNow()
                    val active = repository.getEffectiveWallpaper(forLock)
                    val activeTheme = repository.getEffectiveTheme(forLock)
                    val settings = repository.readSettings()
                    val newRevision = prefs.getLong("content_revision", 0L)
                    val desired = computeDesiredEngineMode()
                    val itemChanged = active.id != currentActiveId || newRevision != currentRevision
                    val themeChanged = activeTheme?.id != currentThemeId ||
                        settings.themeTransparentBackground != currentThemeTransparent
                    val modeChanged = desired != engineMode

                    if (modeChanged || themeChanged || (itemChanged && (desired == EngineMode.VIDEO_DIRECT ||
                            desired == EngineMode.VIDEO_THEME_PRESENTATION ||
                            desired == EngineMode.GIF_CANVAS))
                    ) {
                        rebuildRenderer(holder, surfaceWidth, surfaceHeight)
                    } else if (itemChanged && (engineMode == EngineMode.WEB_PRESENTATION ||
                            engineMode == EngineMode.WEB_CANVAS)
                    ) {
                        currentActiveId = active.id
                        currentRevision = newRevision
                        loadActiveWebWallpaper()
                    }
                }
                syncActivePlaybackAndVisibility()
            }
        }

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameScheduled = false
                if (!isEngineVisible || !shouldRenderLiveContentNow()) return
                if (engineMode != EngineMode.WEB_CANVAS && engineMode != EngineMode.GIF_CANVAS) return

                val targetFps = if (engineMode == EngineMode.GIF_CANVAS) {
                    30
                } else {
                    repository.readSettings().targetFps.coerceIn(15, 60)
                }
                val minIntervalNanos = 1_000_000_000L / targetFps
                if (frameTimeNanos - lastFrameNanos >= minIntervalNanos) {
                    lastFrameNanos = frameTimeNanos
                    when (engineMode) {
                        EngineMode.GIF_CANVAS -> drawGifToCanvas()
                        EngineMode.WEB_CANVAS -> drawWebViewToCanvas()
                        else -> Unit
                    }
                }
                scheduleNextCanvasFrame()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            repository = WallpaperRepository(applicationContext)
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            setTouchEventsEnabled(true)
            repository.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
            registerAudioPlaybackWatcher()
            registerLockStateReceiver()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width <= 0 || height <= 0) return
            mainHandler.post {
                val surface = holder.surface
                if (surface == null || !surface.isValid) return@post

                val forLock = isLockScreenNow()
                val active = repository.getEffectiveWallpaper(forLock)
                val activeTheme = repository.getEffectiveTheme(forLock)
                val settings = repository.readSettings()
                val revision = repository.getPrefs().getLong("content_revision", 0L)
                val desired = computeDesiredEngineMode()
                val sameRendererState = engineMode != EngineMode.NONE &&
                    engineMode == desired &&
                    currentSurface === surface &&
                    surfaceWidth == width &&
                    surfaceHeight == height &&
                    currentActiveId == active.id &&
                    currentThemeId == activeTheme?.id &&
                    currentThemeTransparent == settings.themeTransparentBackground &&
                    currentRevision == revision

                surfaceWidth = width
                surfaceHeight = height

                if (sameRendererState) {
                    syncActivePlaybackAndVisibility()
                } else {
                    rebuildRenderer(holder, width, height)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible
            mainHandler.removeCallbacks(unlockResyncRunnable)
            mainHandler.post {
                if (visible) {
                    refreshRendererIfScreenContentChanged()
                    checkOtherMediaBeforeUnmuting()
                    syncActivePlaybackAndVisibility()
                    scheduleUnlockResyncs()
                } else {
                    syncActivePlaybackAndVisibility()
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event == null || !shouldRenderLiveContentNow()) return
            val settings = repository.readSettings()
            if (!settings.touchEnabled) return
            val targetTheme = themeWebView
            val targetWp = if (engineMode == EngineMode.WEB_PRESENTATION || engineMode == EngineMode.WEB_CANVAS) {
                webView
            } else {
                null
            }
            if (targetTheme == null && targetWp == null) return

            val copy = MotionEvent.obtain(event)
            mainHandler.post {
                try {
                    targetTheme?.dispatchTouchEvent(copy)
                    targetWp?.dispatchTouchEvent(copy)
                } catch (_: Throwable) {
                } finally {
                    copy.recycle()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isEngineVisible = false
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.post {
                stopCanvasLoop()
                tearDownRenderer()
                currentSurface = null
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            unregisterLockStateReceiver()
            unregisterAudioPlaybackWatcher()
            repository.getPrefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
            repository.release()
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.post {
                stopCanvasLoop()
                tearDownRenderer()
                currentSurface = null
            }
        }

        private fun scheduleUnlockResyncs() {
            mainHandler.removeCallbacks(unlockResyncRunnable)
            mainHandler.postDelayed(unlockResyncRunnable, 160L)
            mainHandler.postDelayed(unlockResyncRunnable, 420L)
        }

        private fun currentWallpaperFlags(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                wallpaperFlags
            } else {
                0
            }
        }

        private fun isKeyguardLockedNow(): Boolean =
            runCatching { keyguardManager.isKeyguardLocked }.getOrDefault(false)

        private fun isLockScreenNow(): Boolean {
            val settings = repository.readSettings()
            if (isPreview) {
                return settings.wallpaperTarget == WallpaperTarget.LOCK_ONLY ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        currentWallpaperFlags() == WallpaperManager.FLAG_LOCK)
            }
            return WallpaperPlacementPolicy.isLockScreenContext(
                sdkInt = Build.VERSION.SDK_INT,
                wallpaperFlags = currentWallpaperFlags(),
                isKeyguardLocked = isKeyguardLockedNow()
            )
        }

        private fun refreshRendererIfScreenContentChanged() {
            val holder = surfaceHolder ?: return
            val surface = holder.surface ?: return
            if (!surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return
            val forLock = isLockScreenNow()
            val active = repository.getEffectiveWallpaper(forLock)
            val activeTheme = repository.getEffectiveTheme(forLock)
            val desired = computeDesiredEngineMode()
            if (active.id != currentActiveId ||
                activeTheme?.id != currentThemeId ||
                desired != engineMode
            ) {
                rebuildRenderer(holder, surfaceWidth, surfaceHeight)
            }
        }

        private fun shouldRenderLiveContentNow(): Boolean {
            val target = repository.readSettings().wallpaperTarget
            return WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = isPreview,
                target = target,
                sdkInt = Build.VERSION.SDK_INT,
                wallpaperFlags = currentWallpaperFlags(),
                isKeyguardLocked = isKeyguardLockedNow()
            )
        }

        private fun computeDesiredEngineMode(): EngineMode {
            val settings = repository.readSettings()
            val flags = currentWallpaperFlags()
            val dedicated = WallpaperPlacementPolicy.isDedicatedSingleScreenEngine(
                Build.VERSION.SDK_INT,
                flags
            )
            if (dedicated && !shouldRenderLiveContentNow()) {
                return EngineMode.DEDICATED_INACTIVE_MASK
            }

            val forLock = isLockScreenNow()
            val active = repository.getEffectiveWallpaper(forLock)
            val activeTheme = repository.getEffectiveTheme(forLock)
            val useUploadedWallpaperBehindTheme = settings.themeTransparentBackground

            // When an HTML Theme is active and user turned OFF "Use Uploaded Wallpaper",
            // show the HTML Theme's own built-in wallpaper & view directly without running the uploaded wallpaper underneath.
            if (activeTheme != null && !useUploadedWallpaperBehindTheme) {
                val useHardware = settings.renderMode == RenderEngineMode.AUTO_HARDWARE &&
                    rendererCrashCount == 0
                return if (useHardware) EngineMode.WEB_PRESENTATION else EngineMode.WEB_CANVAS
            }

            if (active.source == WallpaperSource.UPLOADED_VIDEO) {
                val videoFile = repository.getMediaFile(active)
                if (videoFile != null && videoFile.exists()) {
                    return if (activeTheme != null) {
                        EngineMode.VIDEO_THEME_PRESENTATION
                    } else {
                        EngineMode.VIDEO_DIRECT
                    }
                }
            }

            if (active.source == WallpaperSource.UPLOADED_GIF) {
                val gifFile = repository.getMediaFile(active)
                if (gifFile != null && gifFile.exists()) {
                    return EngineMode.GIF_CANVAS
                }
            }

            val useHardware = settings.renderMode == RenderEngineMode.AUTO_HARDWARE &&
                rendererCrashCount == 0
            return if (useHardware) EngineMode.WEB_PRESENTATION else EngineMode.WEB_CANVAS
        }

        private fun rebuildRenderer(holder: SurfaceHolder, width: Int, height: Int) {
            tearDownRenderer()
            val surface = holder.surface
            if (surface == null || !surface.isValid) return

            currentSurface = surface
            val forLock = isLockScreenNow()
            val active = repository.getEffectiveWallpaper(forLock)
            val activeTheme = repository.getEffectiveTheme(forLock)
            val settings = repository.readSettings()
            currentActiveId = active.id
            currentThemeId = activeTheme?.id
            currentThemeTransparent = settings.themeTransparentBackground
            currentRevision = repository.getPrefs().getLong("content_revision", 0L)

            val desired = computeDesiredEngineMode()
            when (desired) {
                EngineMode.DEDICATED_INACTIVE_MASK -> {
                    engineMode = EngineMode.DEDICATED_INACTIVE_MASK
                    clearCanvasSurfaceToBlack()
                }

                EngineMode.VIDEO_THEME_PRESENTATION -> {
                    val videoFile = repository.getMediaFile(active)
                    if (videoFile != null && videoFile.exists()) {
                        if (activeTheme != null && startVideoThemePresentation(surface, width, height, videoFile.absolutePath, activeTheme.id)) {
                            engineMode = EngineMode.VIDEO_THEME_PRESENTATION
                            loadActiveThemeOverlay()
                            syncActivePlaybackAndVisibility()
                            return
                        }
                        // Fallback to universal direct MediaPlayer so video never goes black
                        engineMode = EngineMode.VIDEO_DIRECT
                        startDirectVideoEngine(holder, videoFile.absolutePath)
                        return
                    }
                    startWebEngine(surface, width, height, preferHardware = false)
                }

                EngineMode.VIDEO_DIRECT -> {
                    val videoFile = repository.getMediaFile(active)
                    if (videoFile != null && videoFile.exists()) {
                        engineMode = EngineMode.VIDEO_DIRECT
                        startDirectVideoEngine(holder, videoFile.absolutePath)
                        return
                    }
                    startWebEngine(surface, width, height, preferHardware = false)
                }

                EngineMode.GIF_CANVAS -> {
                    val gifFile = repository.getMediaFile(active)
                    val renderer = GifWallpaperRenderer()
                    if (gifFile != null && renderer.load(gifFile)) {
                        gifRenderer = renderer
                        engineMode = EngineMode.GIF_CANVAS
                        if (activeTheme != null) {
                            attachCanvasThemeWebView(width, height)
                            loadActiveThemeOverlay()
                        }
                        syncActivePlaybackAndVisibility()
                        return
                    }
                    renderer.release()
                    startWebEngine(surface, width, height, preferHardware = false)
                }

                EngineMode.WEB_PRESENTATION -> {
                    startWebEngine(surface, width, height, preferHardware = true)
                }

                EngineMode.WEB_CANVAS, EngineMode.NATIVE_FALLBACK_CANVAS, EngineMode.NONE -> {
                    startWebEngine(surface, width, height, preferHardware = false)
                }
            }
        }

        private fun startWebEngine(
            surface: Surface,
            width: Int,
            height: Int,
            preferHardware: Boolean
        ) {
            val activeTheme = repository.getEffectiveTheme(isLockScreenNow())
            val settings = repository.readSettings()
            val themeOwnsWallpaper = activeTheme != null && !settings.themeTransparentBackground

            if (themeOwnsWallpaper) {
                // Render the uploaded HTML Theme directly with its own wallpaper/background
                if (preferHardware && startWebPresentation(surface, width, height, includeTheme = false)) {
                    engineMode = EngineMode.WEB_PRESENTATION
                    loadUrlInPrimaryWebView(repository.resolveThemeUrl(activeTheme))
                    syncActivePlaybackAndVisibility()
                    return
                }
                if (startCanvasFallbackWebView(width, height)) {
                    engineMode = EngineMode.WEB_CANVAS
                    loadUrlInPrimaryWebView(repository.resolveThemeUrl(activeTheme))
                    syncActivePlaybackAndVisibility()
                } else {
                    engineMode = EngineMode.NATIVE_FALLBACK_CANVAS
                    drawNativeFallbackCanvas()
                }
                return
            }

            if (preferHardware && startWebPresentation(surface, width, height, includeTheme = activeTheme != null)) {
                engineMode = EngineMode.WEB_PRESENTATION
                loadActiveWebWallpaper()
                if (activeTheme != null) loadActiveThemeOverlay()
                syncActivePlaybackAndVisibility()
                return
            }
            if (startCanvasFallbackWebView(width, height)) {
                engineMode = EngineMode.WEB_CANVAS
                loadActiveWebWallpaper()
                if (activeTheme != null) {
                    attachCanvasThemeWebView(width, height)
                    loadActiveThemeOverlay()
                }
                syncActivePlaybackAndVisibility()
            } else {
                engineMode = EngineMode.NATIVE_FALLBACK_CANVAS
                drawNativeFallbackCanvas()
            }
        }

        private fun syncActivePlaybackAndVisibility() {
            val allowedNow = shouldRenderLiveContentNow()
            val activeNow = isEngineVisible && allowedNow
            val modeStr = if (isKeyguardLockedNow()) "lock" else "home"

            when (engineMode) {
                EngineMode.DEDICATED_INACTIVE_MASK -> {
                    if (isEngineVisible) clearCanvasSurfaceToBlack()
                }

                EngineMode.VIDEO_DIRECT -> {
                    val mp = mediaPlayer ?: return
                    if (activeNow && isVideoPrepared) {
                        applyVideoVolume(mp)
                        runCatching { if (!mp.isPlaying) mp.start() }
                    } else {
                        runCatching {
                            mp.setVolume(0f, 0f)
                            if (isVideoPrepared && mp.isPlaying) mp.pause()
                        }
                    }
                }

                EngineMode.VIDEO_THEME_PRESENTATION -> {
                    presentation?.setMasked(!allowedNow)
                    val mp = mediaPlayer
                    if (activeNow && mp != null && isVideoPrepared) {
                        applyVideoVolume(mp)
                        runCatching { if (!mp.isPlaying) mp.start() }
                    } else if (mp != null) {
                        runCatching {
                            mp.setVolume(0f, 0f)
                            if (isVideoPrepared && mp.isPlaying) mp.pause()
                        }
                    }
                    syncThemeWebViewState(activeNow, modeStr)
                }

                EngineMode.GIF_CANVAS -> {
                    val renderer = gifRenderer
                    syncThemeWebViewState(activeNow, modeStr)
                    if (activeNow && renderer != null) {
                        renderer.setPlaying(true)
                        drawGifToCanvas()
                        scheduleNextCanvasFrame()
                    } else {
                        stopCanvasLoop()
                        renderer?.setPlaying(false)
                        if (isEngineVisible && !allowedNow) {
                            clearCanvasSurfaceToBlack()
                        }
                    }
                }

                EngineMode.WEB_PRESENTATION -> {
                    presentation?.setMasked(!allowedNow)
                    if (activeNow) {
                        runCatching {
                            webView?.onResume()
                            webView?.resumeTimers()
                            webView?.invalidate()
                            presentation?.window?.decorView?.invalidate()
                        }
                        applyAudioAndVisibilityState()
                    } else {
                        applyAudioAndVisibilityState()
                        runCatching { webView?.onPause() }
                    }
                    syncThemeWebViewState(activeNow, modeStr)
                }

                EngineMode.WEB_CANVAS -> {
                    syncThemeWebViewState(activeNow, modeStr)
                    if (activeNow) {
                        runCatching {
                            webView?.onResume()
                            webView?.resumeTimers()
                        }
                        applyAudioAndVisibilityState()
                        drawWebViewToCanvas()
                        scheduleNextCanvasFrame()
                    } else {
                        stopCanvasLoop()
                        applyAudioAndVisibilityState()
                        runCatching { webView?.onPause() }
                        if (isEngineVisible && !allowedNow) {
                            clearCanvasSurfaceToBlack()
                        }
                    }
                }

                EngineMode.NATIVE_FALLBACK_CANVAS -> {
                    if (activeNow) {
                        drawNativeFallbackCanvas()
                    } else if (isEngineVisible && !allowedNow) {
                        clearCanvasSurfaceToBlack()
                    }
                }

                EngineMode.NONE -> Unit
            }
        }

        private fun syncThemeWebViewState(activeNow: Boolean, viewMode: String) {
            val tv = themeWebView ?: return
            if (activeNow) {
                runCatching {
                    tv.onResume()
                    tv.resumeTimers()
                    tv.evaluateJavascript(AudioBridgeScript.buildViewModeJs(viewMode), null)
                    tv.invalidate()
                }
                applyAudioAndVisibilityState(tv)
            } else {
                applyAudioAndVisibilityState(tv)
                runCatching { tv.onPause() }
            }
        }

        private fun startDirectVideoEngine(holder: SurfaceHolder, path: String) {
            isVideoPrepared = false
            runCatching {
                val mp = MediaPlayer().apply {
                    setSurface(holder.surface)
                    configureMediaDataSource(this, path)
                    isLooping = true
                    runCatching {
                        setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    }
                    setOnPreparedListener { player ->
                        isVideoPrepared = true
                        runCatching {
                            player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        }
                        syncActivePlaybackAndVisibility()
                    }
                    setOnCompletionListener { player ->
                        if (isEngineVisible && shouldRenderLiveContentNow()) {
                            runCatching {
                                player.seekTo(0)
                                player.start()
                            }
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "Video wallpaper error ($what, $extra)")
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = mp
            }.onFailure {
                Log.w(TAG, "Failed to start direct video wallpaper player", it)
            }
        }

        private fun configureMediaDataSource(mp: MediaPlayer, path: String) {
            val fdSet = runCatching {
                FileInputStream(path).use { fis -> mp.setDataSource(fis.fd) }
                true
            }.getOrDefault(false)
            if (!fdSet) {
                mp.setDataSource(path)
            }
        }

        private fun startVideoThemePresentation(
            surface: Surface,
            width: Int,
            height: Int,
            videoPath: String,
            themeId: String
        ): Boolean {
            return try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = resources.displayMetrics.densityDpi
                val vd = displayManager.createVirtualDisplay(
                    "AnkitVideoThemeDisplay_$themeId",
                    width,
                    height,
                    densityDpi,
                    surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                ) ?: return false

                val displayContext = createDisplayContext(vd.display)
                val textureView = TextureView(displayContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            prepareTextureVideoPlayer(this@apply, st, videoPath)
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            val mp = mediaPlayer
                            if (mp != null && isVideoPrepared) {
                                applyCenterCrop(this@apply, mp.videoWidth, mp.videoHeight)
                            }
                        }
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            releaseMediaPlayerOnly()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
                val themeWv = createConfiguredWebView(
                    context = displayContext,
                    hardwareAccelerated = true,
                    transparent = repository.readSettings().themeTransparentBackground
                )
                val pres = MaskedPresentation(displayContext, vd.display, textureView, themeWv)
                pres.show()

                virtualDisplay = vd
                presentation = pres
                themeWebView = themeWv
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Video+Theme presentation unavailable, falling back to direct video", t)
                tearDownRenderer()
                false
            }
        }

        private fun prepareTextureVideoPlayer(
            textureView: TextureView,
            st: SurfaceTexture,
            videoPath: String
        ) {
            releaseMediaPlayerOnly()
            val surface = Surface(st)
            videoTextureSurface = surface
            runCatching {
                val mp = MediaPlayer().apply {
                    setSurface(surface)
                    configureMediaDataSource(this, videoPath)
                    isLooping = true
                    setOnVideoSizeChangedListener { _, vw, vh ->
                        textureView.post { applyCenterCrop(textureView, vw, vh) }
                    }
                    setOnPreparedListener { player ->
                        isVideoPrepared = true
                        textureView.post { applyCenterCrop(textureView, player.videoWidth, player.videoHeight) }
                        syncActivePlaybackAndVisibility()
                    }
                    setOnCompletionListener { player ->
                        if (isEngineVisible && shouldRenderLiveContentNow()) {
                            runCatching {
                                player.seekTo(0)
                                player.start()
                            }
                        }
                    }
                    setOnErrorListener { _, _, _ -> true }
                    prepareAsync()
                }
                mediaPlayer = mp
            }
        }

        private fun applyCenterCrop(textureView: TextureView, videoW: Int, videoH: Int) {
            val viewW = textureView.width.toFloat()
            val viewH = textureView.height.toFloat()
            if (viewW <= 0f || viewH <= 0f || videoW <= 0 || videoH <= 0) return
            val scaleX = videoW.toFloat() / viewW
            val scaleY = videoH.toFloat() / viewH
            val maxScale = maxOf(1f / scaleX, 1f / scaleY)
            val matrix = Matrix().apply {
                setScale(scaleX * maxScale, scaleY * maxScale, viewW / 2f, viewH / 2f)
            }
            textureView.setTransform(matrix)
        }

        private fun startWebPresentation(
            surface: Surface,
            width: Int,
            height: Int,
            includeTheme: Boolean
        ): Boolean {
            return try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = resources.displayMetrics.densityDpi
                val vd = displayManager.createVirtualDisplay(
                    "AnkitHtmlWallpaperDisplay",
                    width,
                    height,
                    densityDpi,
                    surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                ) ?: return false

                val displayContext = createDisplayContext(vd.display)
                val wv = createConfiguredWebView(displayContext, hardwareAccelerated = true, transparent = false)
                val tv = if (includeTheme) {
                    createConfiguredWebView(
                        displayContext,
                        hardwareAccelerated = true,
                        transparent = repository.readSettings().themeTransparentBackground
                    )
                } else {
                    null
                }
                val pres = MaskedPresentation(displayContext, vd.display, wv, tv)
                pres.show()

                virtualDisplay = vd
                presentation = pres
                webView = wv
                themeWebView = tv
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Hardware presentation unavailable, falling back to Universal Canvas", t)
                tearDownRenderer()
                false
            }
        }

        private fun startCanvasFallbackWebView(width: Int, height: Int): Boolean {
            return try {
                val wv = createConfiguredWebView(applicationContext, hardwareAccelerated = false, transparent = false)
                measureAndLayoutOffscreen(wv, width, height)
                webView = wv
                true
            } catch (t: Throwable) {
                Log.w(TAG, "System WebView unavailable on this device right now", t)
                webView = null
                false
            }
        }

        private fun attachCanvasThemeWebView(width: Int, height: Int) {
            runCatching {
                val tv = createConfiguredWebView(
                    context = applicationContext,
                    hardwareAccelerated = false,
                    transparent = repository.readSettings().themeTransparentBackground
                )
                measureAndLayoutOffscreen(tv, width, height)
                themeWebView = tv
            }
        }

        private fun measureAndLayoutOffscreen(view: View, width: Int, height: Int) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, width, height)
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun createConfiguredWebView(
            context: Context,
            hardwareAccelerated: Boolean,
            transparent: Boolean
        ): WebView {
            return WebView(context).apply {
                setBackgroundColor(if (transparent) Color.TRANSPARENT else Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setLayerType(
                    if (hardwareAccelerated) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE,
                    null
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                webChromeClient = WebChromeClient()
                webViewClient = LocalWallpaperWebClient(
                    context = applicationContext,
                    repository = repository,
                    onPageReady = { loadedView ->
                        applyAudioAndVisibilityState(loadedView)
                        val modeStr = if (isKeyguardLockedNow()) "lock" else "home"
                        runCatching {
                            loadedView.evaluateJavascript(AudioBridgeScript.buildViewModeJs(modeStr), null)
                        }
                        if ((engineMode == EngineMode.WEB_CANVAS || engineMode == EngineMode.GIF_CANVAS) &&
                            isEngineVisible && shouldRenderLiveContentNow()
                        ) {
                            if (engineMode == EngineMode.GIF_CANVAS) drawGifToCanvas() else drawWebViewToCanvas()
                            scheduleNextCanvasFrame()
                        } else if (engineMode == EngineMode.WEB_PRESENTATION || engineMode == EngineMode.VIDEO_THEME_PRESENTATION) {
                            loadedView.invalidate()
                            presentation?.window?.decorView?.invalidate()
                        }
                    },
                    onRendererCrash = { _, _ ->
                        handleRendererCrash()
                    }
                )
                onResume()
                resumeTimers()
            }
        }

        private fun handleRendererCrash() {
            mainHandler.post {
                tearDownRenderer()
                rendererCrashCount++
                if (rendererCrashCount <= MAX_SERVICE_RENDERER_RETRIES &&
                    surfaceWidth > 0 && surfaceHeight > 0
                ) {
                    mainHandler.postDelayed({
                        val holder = surfaceHolder
                        if (holder != null && holder.surface != null && holder.surface.isValid) {
                            rebuildRenderer(holder, surfaceWidth, surfaceHeight)
                        }
                    }, 300L)
                }
            }
        }

        private fun loadActiveWebWallpaper() {
            val forLock = isLockScreenNow()
            val active = repository.getEffectiveWallpaper(forLock)
            val activeTheme = repository.getEffectiveTheme(forLock)
            val settings = repository.readSettings()
            val url = if (activeTheme != null && !settings.themeTransparentBackground) {
                repository.resolveThemeUrl(activeTheme)
            } else {
                repository.resolveWallpaperUrl(active)
            }
            loadUrlInPrimaryWebView(url)
        }

        private fun loadUrlInPrimaryWebView(url: String) {
            runCatching {
                webView?.onResume()
                webView?.resumeTimers()
                webView?.loadUrl(url)
            }
        }

        private fun loadActiveThemeOverlay() {
            val theme = repository.getEffectiveTheme(isLockScreenNow()) ?: return
            val url = repository.resolveThemeUrl(theme)
            runCatching {
                themeWebView?.onResume()
                themeWebView?.resumeTimers()
                themeWebView?.loadUrl(url)
            }
        }

        private fun applyVideoVolume(player: MediaPlayer?) {
            val mp = player ?: return
            val settings = repository.readSettings()
            val activeNow = isEngineVisible && shouldRenderLiveContentNow()
            val allowSound = settings.soundEnabled &&
                activeNow &&
                !(settings.muteDuringOtherMedia && otherAppPlayingAudio)
            val vol = if (allowSound) settings.volume else 0f
            runCatching { mp.setVolume(vol, vol) }
        }

        private fun applyAudioAndVisibilityState(target: WebView? = webView) {
            if (target == null && (engineMode == EngineMode.VIDEO_DIRECT || engineMode == EngineMode.VIDEO_THEME_PRESENTATION)) {
                applyVideoVolume(mediaPlayer)
                return
            }
            val view = target ?: return
            val settings = repository.readSettings()
            val activeNow = isEngineVisible && shouldRenderLiveContentNow()
            val allowSound = settings.soundEnabled &&
                activeNow &&
                !(settings.muteDuringOtherMedia && otherAppPlayingAudio)
            val js = AudioBridgeScript.buildApplyStateJs(
                soundEnabled = allowSound,
                volume = settings.volume,
                visible = activeNow
            )
            runCatching { view.evaluateJavascript(js, null) }
        }

        private fun clearCanvasSurfaceToBlack() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.drawColor(Color.BLACK)
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawNativeFallbackCanvas() {
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val w = canvas.width.toFloat()
                    val h = canvas.height.toFloat()
                    fallbackPaint.shader = LinearGradient(
                        0f, 0f, w, h,
                        intArrayOf(0xFF040712.toInt(), 0xFF0E2A47.toInt(), 0xFF083344.toInt()),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, w, h, fallbackPaint)
                }
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawGifToCanvas() {
            val renderer = gifRenderer ?: return
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return
            var canvas: Canvas? = null
            try {
                val tv = themeWebView
                tv?.invalidate()
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderer.drawFrame(canvas, canvas.width, canvas.height)
                    // Composite uploaded HTML Theme directly over the live GIF frame!
                    tv?.draw(canvas)
                }
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawWebViewToCanvas() {
            val wv = webView ?: return
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                wv.invalidate()
                val tv = themeWebView
                tv?.invalidate()
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    wv.draw(canvas)
                    // Composite uploaded HTML Theme directly over the live HTML Wallpaper!
                    tv?.draw(canvas)
                }
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun scheduleNextCanvasFrame() {
            val isCanvasMode = engineMode == EngineMode.WEB_CANVAS || engineMode == EngineMode.GIF_CANVAS
            if (!frameScheduled && isCanvasMode && isEngineVisible && shouldRenderLiveContentNow()) {
                frameScheduled = true
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }

        private fun stopCanvasLoop() {
            if (frameScheduled) {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                frameScheduled = false
            }
        }

        private fun releaseMediaPlayerOnly() {
            isVideoPrepared = false
            runCatching {
                mediaPlayer?.apply {
                    if (isPlaying) stop()
                    reset()
                    release()
                }
            }
            mediaPlayer = null
            runCatching { videoTextureSurface?.release() }
            videoTextureSurface = null
        }

        private fun destroyWebViewSafely(wv: WebView?) {
            runCatching {
                wv?.apply {
                    stopLoading()
                    webViewClient = WebViewClient()
                    webChromeClient = null
                    onPause()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
            }
        }

        private fun tearDownRenderer() {
            stopCanvasLoop()
            releaseMediaPlayerOnly()
            runCatching { gifRenderer?.release() }
            gifRenderer = null
            destroyWebViewSafely(themeWebView)
            themeWebView = null
            destroyWebViewSafely(webView)
            webView = null
            runCatching { presentation?.dismiss() }
            presentation = null
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            engineMode = EngineMode.NONE
        }

        private fun checkOtherMediaBeforeUnmuting() {
            val settings = repository.readSettings()
            otherAppPlayingAudio = settings.muteDuringOtherMedia && audioManager.isMusicActive
        }

        private fun registerLockStateReceiver() {
            if (lockReceiverRegistered) return
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            runCatching {
                ContextCompat.registerReceiver(
                    applicationContext,
                    lockStateReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                lockReceiverRegistered = true
            }
        }

        private fun unregisterLockStateReceiver() {
            if (!lockReceiverRegistered) return
            runCatching { applicationContext.unregisterReceiver(lockStateReceiver) }
            lockReceiverRegistered = false
        }

        private fun registerAudioPlaybackWatcher() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val cb = object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                        val settings = repository.readSettings()
                        if (!settings.muteDuringOtherMedia) {
                            otherAppPlayingAudio = false
                            applyAudioAndVisibilityState()
                            return
                        }
                        val mediaConfigs = configs.orEmpty().count { cfg ->
                            val usage = cfg.audioAttributes.usage
                            usage == AudioAttributes.USAGE_MEDIA ||
                                usage == AudioAttributes.USAGE_GAME ||
                                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
                        }
                        val wallpaperCurrentlyAllowed = settings.soundEnabled &&
                            isEngineVisible && shouldRenderLiveContentNow() && !otherAppPlayingAudio
                        val externalDetected = if (wallpaperCurrentlyAllowed) {
                            mediaConfigs >= 2
                        } else {
                            mediaConfigs >= 1
                        }
                        if (externalDetected != otherAppPlayingAudio) {
                            otherAppPlayingAudio = externalDetected
                            applyAudioAndVisibilityState()
                        }
                    }
                }
                playbackCallback = cb
                audioManager.registerAudioPlaybackCallback(cb, mainHandler)
            }
        }

        private fun unregisterAudioPlaybackWatcher() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (playbackCallback as? AudioManager.AudioPlaybackCallback)?.let {
                    audioManager.unregisterAudioPlaybackCallback(it)
                }
                playbackCallback = null
            }
        }
    }

    private class MaskedPresentation(
        outerContext: Context,
        display: Display,
        private val contentView: View,
        private val themeOverlayView: View? = null
    ) : Presentation(outerContext, display) {

        private var maskView: View? = null
        private var pendingMasked: Boolean = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.setBackgroundDrawableResource(android.R.color.black)
            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    contentView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                if (themeOverlayView != null) {
                    addView(
                        themeOverlayView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                val mask = View(context).apply {
                    setBackgroundColor(Color.BLACK)
                    visibility = if (pendingMasked) View.VISIBLE else View.GONE
                }
                addView(
                    mask,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                maskView = mask
            }
            setContentView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        fun setMasked(masked: Boolean) {
            pendingMasked = masked
            maskView?.visibility = if (masked) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val TAG = "HtmlLiveWallpaper"
        private const val MAX_SERVICE_RENDERER_RETRIES = 2
    }
}

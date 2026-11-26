package com.example.wallpaper

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Matrix
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

class HtmlLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HtmlWallpaperEngine()

    private enum class EngineMode {
        NONE,
        DEDICATED_INACTIVE_MASK,
        VIDEO_DIRECT,
        VIDEO_PRESENTATION,
        GIF_CANVAS,
        WEB_PRESENTATION,
        WEB_CANVAS
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
        private var videoTextureView: TextureView? = null
        private var videoTextureSurface: Surface? = null
        private var mediaPlayer: MediaPlayer? = null
        private var gifRenderer: GifWallpaperRenderer? = null
        private var isVideoPrepared: Boolean = false

        private var currentSurface: Surface? = null
        private var surfaceWidth: Int = 0
        private var surfaceHeight: Int = 0
        private var currentActiveId: String? = null
        private var currentRevision: Long = -1L
        private var rendererCrashCount: Int = 0
        private var isEngineVisible: Boolean = false
        private var otherAppPlayingAudio: Boolean = false
        private var lastFrameNanos: Long = 0L
        private var frameScheduled: Boolean = false
        private var lockReceiverRegistered: Boolean = false
        private var playbackCallback: Any? = null

        private val unlockResyncRunnable = Runnable {
            if (isEngineVisible) {
                syncActivePlaybackAndVisibility()
            }
        }

        private val lockStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                mainHandler.post {
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
                    val active = repository.getActiveWallpaper()
                    val newRevision = prefs.getLong("content_revision", 0L)
                    val desired = computeDesiredEngineMode()
                    val itemChanged = active.id != currentActiveId || newRevision != currentRevision
                    val modeChanged = desired != engineMode

                    if (modeChanged || (itemChanged && (desired == EngineMode.VIDEO_DIRECT ||
                            desired == EngineMode.VIDEO_PRESENTATION ||
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

                val active = repository.getActiveWallpaper()
                val revision = repository.getPrefs().getLong("content_revision", 0L)
                val desired = computeDesiredEngineMode()
                val sameRendererState = engineMode != EngineMode.NONE &&
                    engineMode == desired &&
                    currentSurface === surface &&
                    surfaceWidth == width &&
                    surfaceHeight == height &&
                    currentActiveId == active.id &&
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
            if (engineMode != EngineMode.WEB_PRESENTATION && engineMode != EngineMode.WEB_CANVAS) return
            val settings = repository.readSettings()
            if (!settings.touchEnabled) return
            val copy = MotionEvent.obtain(event)
            mainHandler.post {
                try {
                    webView?.dispatchTouchEvent(copy)
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

        private fun shouldRenderLiveContentNow(): Boolean {
            val target = repository.readSettings().wallpaperTarget
            val isLocked = runCatching { keyguardManager.isKeyguardLocked }.getOrDefault(false)
            return WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = isPreview,
                target = target,
                sdkInt = Build.VERSION.SDK_INT,
                wallpaperFlags = currentWallpaperFlags(),
                isKeyguardLocked = isLocked
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

            val active = repository.getActiveWallpaper()
            if (active.source == WallpaperSource.UPLOADED_VIDEO) {
                val videoFile = repository.getMediaFile(active)
                if (videoFile != null && videoFile.exists()) {
                    val needsDynamicMask = WallpaperPlacementPolicy.needsDynamicScreenMasking(
                        isPreview = isPreview,
                        target = settings.wallpaperTarget,
                        sdkInt = Build.VERSION.SDK_INT,
                        wallpaperFlags = flags
                    )
                    return if (needsDynamicMask) {
                        EngineMode.VIDEO_PRESENTATION
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

            val forceCanvas = settings.renderMode == RenderEngineMode.COMPATIBILITY_CANVAS ||
                rendererCrashCount > 0
            return if (forceCanvas) EngineMode.WEB_CANVAS else EngineMode.WEB_PRESENTATION
        }

        private fun rebuildRenderer(holder: SurfaceHolder, width: Int, height: Int) {
            tearDownRenderer()
            val surface = holder.surface
            if (surface == null || !surface.isValid) return

            currentSurface = surface
            val active = repository.getActiveWallpaper()
            currentActiveId = active.id
            currentRevision = repository.getPrefs().getLong("content_revision", 0L)

            val desired = computeDesiredEngineMode()
            when (desired) {
                EngineMode.DEDICATED_INACTIVE_MASK -> {
                    engineMode = EngineMode.DEDICATED_INACTIVE_MASK
                    clearCanvasSurfaceToBlack()
                }

                EngineMode.VIDEO_PRESENTATION -> {
                    val videoFile = repository.getMediaFile(active)
                    if (videoFile != null && videoFile.exists()) {
                        val started = startVideoPresentation(surface, width, height, videoFile.absolutePath)
                        if (started) {
                            engineMode = EngineMode.VIDEO_PRESENTATION
                            syncActivePlaybackAndVisibility()
                            return
                        }
                        // Fallback to direct MediaPlayer (never mixing lockCanvas on this Surface)
                        engineMode = EngineMode.VIDEO_DIRECT
                        startDirectVideoEngine(holder, videoFile.absolutePath)
                        return
                    }
                    startWebFallback(surface, width, height, forceCanvas = false)
                }

                EngineMode.VIDEO_DIRECT -> {
                    val videoFile = repository.getMediaFile(active)
                    if (videoFile != null && videoFile.exists()) {
                        engineMode = EngineMode.VIDEO_DIRECT
                        startDirectVideoEngine(holder, videoFile.absolutePath)
                        return
                    }
                    startWebFallback(surface, width, height, forceCanvas = false)
                }

                EngineMode.GIF_CANVAS -> {
                    val gifFile = repository.getMediaFile(active)
                    val renderer = GifWallpaperRenderer()
                    if (gifFile != null && renderer.load(gifFile)) {
                        gifRenderer = renderer
                        engineMode = EngineMode.GIF_CANVAS
                        syncActivePlaybackAndVisibility()
                        return
                    }
                    renderer.release()
                    startWebFallback(surface, width, height, forceCanvas = true)
                }

                EngineMode.WEB_PRESENTATION -> {
                    startWebFallback(surface, width, height, forceCanvas = false)
                }

                EngineMode.WEB_CANVAS, EngineMode.NONE -> {
                    startWebFallback(surface, width, height, forceCanvas = true)
                }
            }
        }

        private fun startWebFallback(
            surface: Surface,
            width: Int,
            height: Int,
            forceCanvas: Boolean
        ) {
            if (!forceCanvas && startWebPresentation(surface, width, height)) {
                engineMode = EngineMode.WEB_PRESENTATION
                loadActiveWebWallpaper()
                syncActivePlaybackAndVisibility()
                return
            }
            engineMode = EngineMode.WEB_CANVAS
            startCanvasFallbackWebView(width, height)
            loadActiveWebWallpaper()
            syncActivePlaybackAndVisibility()
        }

        private fun syncActivePlaybackAndVisibility() {
            val allowedNow = shouldRenderLiveContentNow()
            val activeNow = isEngineVisible && allowedNow

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

                EngineMode.VIDEO_PRESENTATION -> {
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
                }

                EngineMode.GIF_CANVAS -> {
                    val renderer = gifRenderer
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
                        // NEVER call webView.pauseTimers() (it freezes WebViews process-wide)
                        // and NEVER set webView.visibility = INVISIBLE (causes GPU compositor black frames)
                        runCatching { webView?.onPause() }
                    }
                }

                EngineMode.WEB_CANVAS -> {
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

                EngineMode.NONE -> Unit
            }
        }

        private fun startDirectVideoEngine(holder: SurfaceHolder, path: String) {
            isVideoPrepared = false
            runCatching {
                val mp = MediaPlayer().apply {
                    setSurface(holder.surface)
                    setDataSource(path)
                    isLooping = true
                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    setOnPreparedListener {
                        isVideoPrepared = true
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

        private fun startVideoPresentation(
            surface: Surface,
            width: Int,
            height: Int,
            videoPath: String
        ): Boolean {
            return try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = resources.displayMetrics.densityDpi
                val vd = displayManager.createVirtualDisplay(
                    "AnkitVideoWallpaperDisplay",
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
                val pres = MaskedPresentation(displayContext, vd.display, textureView)
                pres.show()

                virtualDisplay = vd
                presentation = pres
                videoTextureView = textureView
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Video presentation unavailable, using direct MediaPlayer", t)
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
                    setDataSource(videoPath)
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

        private fun startWebPresentation(surface: Surface, width: Int, height: Int): Boolean {
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
                val wv = createConfiguredWebView(displayContext, hardwareAccelerated = true)
                val pres = MaskedPresentation(displayContext, vd.display, wv)
                pres.show()

                virtualDisplay = vd
                presentation = pres
                webView = wv
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Hardware presentation unavailable, falling back to Canvas mode", t)
                tearDownRenderer()
                false
            }
        }

        private fun startCanvasFallbackWebView(width: Int, height: Int) {
            val wv = createConfiguredWebView(applicationContext, hardwareAccelerated = false)
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            wv.measure(widthSpec, heightSpec)
            wv.layout(0, 0, width, height)
            webView = wv
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun createConfiguredWebView(
            context: Context,
            hardwareAccelerated: Boolean
        ): WebView {
            return WebView(context).apply {
                setBackgroundColor(Color.BLACK)
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
                        if (engineMode == EngineMode.WEB_CANVAS && isEngineVisible && shouldRenderLiveContentNow()) {
                            drawWebViewToCanvas()
                            scheduleNextCanvasFrame()
                        } else if (engineMode == EngineMode.WEB_PRESENTATION) {
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
                    Log.w(TAG, "Recovering wallpaper WebView in compatibility mode (attempt $rendererCrashCount)")
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
            val active = repository.getActiveWallpaper()
            val url = repository.resolveWallpaperUrl(active)
            runCatching {
                webView?.onResume()
                webView?.resumeTimers()
                webView?.loadUrl(url)
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
            if (engineMode == EngineMode.VIDEO_DIRECT || engineMode == EngineMode.VIDEO_PRESENTATION) {
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
            // Only ever called for CPU-canvas surfaces (GIF_CANVAS, WEB_CANVAS, DEDICATED_INACTIVE_MASK)
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: android.graphics.Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.drawColor(Color.BLACK)
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        private fun drawGifToCanvas() {
            val renderer = gifRenderer ?: return
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid || surfaceWidth <= 0 || surfaceHeight <= 0) return
            var canvas: android.graphics.Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    renderer.drawFrame(canvas, canvas.width, canvas.height)
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
            var canvas: android.graphics.Canvas? = null
            try {
                wv.invalidate()
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    wv.draw(canvas)
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

        private fun tearDownRenderer() {
            stopCanvasLoop()
            releaseMediaPlayerOnly()
            videoTextureView = null
            runCatching { gifRenderer?.release() }
            gifRenderer = null
            runCatching {
                webView?.apply {
                    stopLoading()
                    webViewClient = WebViewClient()
                    webChromeClient = null
                    onPause()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
            }
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

    /**
     * Presentation hosting the wallpaper content with a sibling black mask View so we can mask
     * inactive screens (Home-Only / Lock-Only) WITHOUT ever toggling WebView.visibility = INVISIBLE
     * or calling lockCanvas() on a hardware EGL/MediaCodec surface.
     */
    private class MaskedPresentation(
        outerContext: Context,
        display: Display,
        private val contentView: View
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

package com.example.wallpaper

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.RenderEngineMode
import com.example.data.WallpaperRepository

class HtmlLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HtmlWallpaperEngine()

    inner class HtmlWallpaperEngine : Engine() {

        private val mainHandler = Handler(Looper.getMainLooper())
        private lateinit var repository: WallpaperRepository
        private lateinit var audioManager: AudioManager

        private var virtualDisplay: VirtualDisplay? = null
        private var presentation: WallpaperPresentation? = null
        private var webView: WebView? = null

        private var surfaceWidth: Int = 0
        private var surfaceHeight: Int = 0
        private var currentLoadedUrl: String? = null
        private var currentRevision: Long = -1L
        private var usingCanvasFallback: Boolean = false
        private var rendererCrashCount: Int = 0
        private var isEngineVisible: Boolean = false
        private var otherAppPlayingAudio: Boolean = false
        private var lastFrameNanos: Long = 0L
        private var frameScheduled: Boolean = false

        private var playbackCallback: Any? = null

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
            mainHandler.post {
                val newSettings = repository.readSettings()
                val newRevision = prefs.getLong("content_revision", 0L)
                val targetUrl = repository.resolveWallpaperUrl(repository.getActiveWallpaper())

                val wantsFallback = newSettings.renderMode == RenderEngineMode.COMPATIBILITY_CANVAS
                if (wantsFallback != usingCanvasFallback && surfaceWidth > 0 && surfaceHeight > 0 && rendererCrashCount == 0) {
                    rebuildRenderer(surfaceHolder, surfaceWidth, surfaceHeight, forceCanvasFallback = wantsFallback)
                } else if (targetUrl != currentLoadedUrl || newRevision != currentRevision) {
                    currentRevision = newRevision
                    loadActiveWallpaper()
                }
                applyAudioAndVisibilityState()
            }
        }

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameScheduled = false
                if (!usingCanvasFallback || !isEngineVisible) return
                val settings = repository.readSettings()
                val minIntervalNanos = 1_000_000_000L / settings.targetFps.coerceIn(15, 60)
                if (frameTimeNanos - lastFrameNanos >= minIntervalNanos) {
                    lastFrameNanos = frameTimeNanos
                    drawWebViewToCanvas()
                }
                scheduleNextCanvasFrame()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            repository = WallpaperRepository(applicationContext)
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            setTouchEventsEnabled(true)
            repository.getPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
            registerAudioPlaybackWatcher()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width <= 0 || height <= 0) return
            surfaceWidth = width
            surfaceHeight = height
            mainHandler.post {
                val settings = repository.readSettings()
                val forceFallback = settings.renderMode == RenderEngineMode.COMPATIBILITY_CANVAS ||
                    rendererCrashCount > 0
                rebuildRenderer(holder, width, height, forceCanvasFallback = forceFallback)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible
            mainHandler.post {
                if (visible) {
                    checkOtherMediaBeforeUnmuting()
                    runCatching {
                        webView?.onResume()
                        webView?.resumeTimers()
                    }
                    applyAudioAndVisibilityState()
                    if (usingCanvasFallback) {
                        scheduleNextCanvasFrame()
                    }
                } else {
                    stopCanvasLoop()
                    applyAudioAndVisibilityState()
                    runCatching {
                        webView?.onPause()
                        webView?.pauseTimers()
                    }
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event == null) return
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
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            unregisterAudioPlaybackWatcher()
            repository.getPrefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
            repository.release()
            mainHandler.removeCallbacksAndMessages(null)
            mainHandler.post {
                stopCanvasLoop()
                tearDownRenderer()
            }
        }

        private fun rebuildRenderer(
            holder: SurfaceHolder,
            width: Int,
            height: Int,
            forceCanvasFallback: Boolean
        ) {
            tearDownRenderer()
            val surface = holder.surface
            if (surface == null || !surface.isValid) return

            if (!forceCanvasFallback) {
                val startedHardware = startHardwarePresentation(surface, width, height)
                if (startedHardware) {
                    usingCanvasFallback = false
                    loadActiveWallpaper()
                    applyAudioAndVisibilityState()
                    return
                }
            }

            // Fallback to universal software Canvas rendering
            usingCanvasFallback = true
            startCanvasFallbackWebView(width, height)
            loadActiveWallpaper()
            applyAudioAndVisibilityState()
            if (isEngineVisible) {
                scheduleNextCanvasFrame()
            }
        }

        private fun startHardwarePresentation(
            surface: android.view.Surface,
            width: Int,
            height: Int
        ): Boolean {
            return try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val densityDpi = resources.displayMetrics.densityDpi
                val vd = displayManager.createVirtualDisplay(
                    "LiveHtmlWallpaperDisplay",
                    width,
                    height,
                    densityDpi,
                    surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                ) ?: return false

                val displayContext = createDisplayContext(vd.display)
                val wv = createConfiguredWebView(displayContext, hardwareAccelerated = true)
                val pres = WallpaperPresentation(displayContext, vd.display, wv)
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
                    },
                    onRendererCrash = { _, _ ->
                        handleRendererCrash()
                    }
                )
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
                            rebuildRenderer(holder, surfaceWidth, surfaceHeight, forceCanvasFallback = true)
                        }
                    }, 350L)
                }
            }
        }

        private fun loadActiveWallpaper() {
            val active = repository.getActiveWallpaper()
            val url = repository.resolveWallpaperUrl(active)
            currentLoadedUrl = url
            currentRevision = repository.getPrefs().getLong("content_revision", 0L)
            runCatching { webView?.loadUrl(url) }
        }

        private fun applyAudioAndVisibilityState(target: WebView? = webView) {
            val view = target ?: return
            val settings = repository.readSettings()
            val allowSound = settings.soundEnabled &&
                isEngineVisible &&
                !(settings.muteDuringOtherMedia && otherAppPlayingAudio)
            val js = AudioBridgeScript.buildApplyStateJs(
                soundEnabled = allowSound,
                volume = settings.volume,
                visible = isEngineVisible
            )
            runCatching { view.evaluateJavascript(js, null) }
        }

        private fun drawWebViewToCanvas() {
            val wv = webView ?: return
            val holder = surfaceHolder ?: return
            if (!holder.surface.isValid) return
            var canvas: android.graphics.Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.BLACK)
                    wv.draw(canvas)
                }
            } catch (_: Throwable) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        private fun scheduleNextCanvasFrame() {
            if (!frameScheduled && usingCanvasFallback && isEngineVisible) {
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

        private fun tearDownRenderer() {
            stopCanvasLoop()
            try {
                webView?.apply {
                    stopLoading()
                    webViewClient = WebViewClient()
                    webChromeClient = null
                    onPause()
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
            } catch (_: Throwable) {
            }
            webView = null
            try {
                presentation?.dismiss()
            } catch (_: Throwable) {
            }
            presentation = null
            try {
                virtualDisplay?.release()
            } catch (_: Throwable) {
            }
            virtualDisplay = null
        }

        private fun checkOtherMediaBeforeUnmuting() {
            val settings = repository.readSettings()
            if (!settings.muteDuringOtherMedia) {
                otherAppPlayingAudio = false
                return
            }
            otherAppPlayingAudio = audioManager.isMusicActive
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
                            isEngineVisible && !otherAppPlayingAudio
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

    private class WallpaperPresentation(
        outerContext: Context,
        display: android.view.Display,
        private val contentWebView: WebView
    ) : Presentation(outerContext, display) {

        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            super.onCreate(savedInstanceState)
            window?.setBackgroundDrawableResource(android.R.color.black)
            setContentView(
                contentWebView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    companion object {
        private const val TAG = "HtmlLiveWallpaper"
        private const val MAX_SERVICE_RENDERER_RETRIES = 2
    }
}

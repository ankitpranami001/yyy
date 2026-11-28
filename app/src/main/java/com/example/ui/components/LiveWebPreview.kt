package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.WallpaperRepository
import com.example.wallpaper.AudioBridgeScript
import com.example.wallpaper.LocalWallpaperWebClient
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun LiveWebPreview(
    url: String,
    repository: WallpaperRepository,
    soundEnabled: Boolean,
    volume: Float,
    touchEnabled: Boolean,
    reloadKey: Any = 0,
    inlineHtml: String? = null,
    preferSoftwareLayer: Boolean = true,
    transparentBackground: Boolean = false,
    viewMode: String? = null,
    modifier: Modifier = Modifier
) {
    var recoveryCount by remember(url, reloadKey) { mutableIntStateOf(0) }
    var useSoftwareLayer by remember(preferSoftwareLayer) { mutableStateOf(preferSoftwareLayer) }
    var rendererFailedPermanently by remember(url, reloadKey) { mutableStateOf(false) }

    if (rendererFailedPermanently) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preview paused after renderer reset",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            FilledTonalButton(
                onClick = {
                    rendererFailedPermanently = false
                    useSoftwareLayer = true
                    recoveryCount++
                },
                modifier = Modifier.testTag("reload_preview_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text("Reload Preview")
            }
        }
        return
    }

    key(recoveryCount) {
        ManagedLiveWebView(
            url = url,
            repository = repository,
            soundEnabled = soundEnabled,
            volume = volume,
            touchEnabled = touchEnabled,
            reloadKey = reloadKey,
            inlineHtml = inlineHtml,
            useSoftwareLayer = useSoftwareLayer,
            transparentBackground = transparentBackground,
            viewMode = viewMode,
            onRendererCrashed = {
                if (recoveryCount < MAX_RENDERER_RECOVERIES) {
                    useSoftwareLayer = true
                    recoveryCount++
                } else {
                    rendererFailedPermanently = true
                }
            },
            modifier = modifier
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun ManagedLiveWebView(
    url: String,
    repository: WallpaperRepository,
    soundEnabled: Boolean,
    volume: Float,
    touchEnabled: Boolean,
    reloadKey: Any,
    inlineHtml: String?,
    useSoftwareLayer: Boolean,
    transparentBackground: Boolean,
    viewMode: String?,
    onRendererCrashed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestInlineHtml by rememberUpdatedState(inlineHtml)
    val latestSound by rememberUpdatedState(soundEnabled)
    val latestVolume by rememberUpdatedState(volume)
    val latestTouch by rememberUpdatedState(touchEnabled)
    val latestOnCrash by rememberUpdatedState(onRendererCrashed)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isResumed by remember { mutableStateOf(true) }
    var pageLoaded by remember { mutableStateOf(false) }
    var lastLoadedSignature by remember { mutableStateOf<Triple<String, Any, Int?>?>(null) }
    var isDestroyed by remember { mutableStateOf(false) }

    fun pushAudioState(view: WebView?, visible: Boolean) {
        if (view == null || isDestroyed || !pageLoaded) return
        runCatching {
            val js = AudioBridgeScript.buildApplyStateJs(
                soundEnabled = latestSound && visible,
                volume = latestVolume,
                visible = visible
            )
            view.evaluateJavascript(js, null)
        }
    }

    fun safelyDestroyWebView(wv: WebView?) {
        if (wv == null || isDestroyed) return
        isDestroyed = true
        pageLoaded = false
        runCatching {
            wv.stopLoading()
            wv.webViewClient = WebViewClient()
            wv.webChromeClient = null
            wv.onPause()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webViewRef = null
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val wv = webViewRef ?: return@LifecycleEventObserver
            if (isDestroyed) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isResumed = true
                    runCatching {
                        wv.onResume()
                        wv.resumeTimers()
                        wv.invalidate()
                    }
                    pushAudioState(wv, true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isResumed = false
                    pushAudioState(wv, false)
                    // NEVER call wv.pauseTimers() — it freezes HtmlLiveWallpaperService WebView globally!
                    runCatching { wv.onPause() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val wv = webViewRef
            if (wv != null && !isDestroyed) {
                pushAudioState(wv, false)
                safelyDestroyWebView(wv)
            }
        }
    }

    LaunchedEffect(soundEnabled, volume, isResumed, pageLoaded, webViewRef) {
        pushAudioState(webViewRef, isResumed)
    }

    LaunchedEffect(viewMode, pageLoaded, webViewRef) {
        val mode = viewMode ?: return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        if (!pageLoaded || isDestroyed) return@LaunchedEffect
        runCatching { wv.evaluateJavascript(AudioBridgeScript.buildViewModeJs(mode), null) }
    }

    val currentSignature = Triple(url, reloadKey, inlineHtml?.hashCode())
    LaunchedEffect(currentSignature, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (isDestroyed) return@LaunchedEffect
        if (lastLoadedSignature != currentSignature) {
            lastLoadedSignature = currentSignature
            pageLoaded = false
            runCatching {
                wv.onResume()
                wv.resumeTimers()
                wv.loadUrl(url)
            }
        }
    }

    // Safety timeout so loading indicator never sticks if an HTML page has slow external resources
    LaunchedEffect(currentSignature, pageLoaded) {
        if (!pageLoaded) {
            delay(2500L)
            pageLoaded = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(if (transparentBackground) Color.TRANSPARENT else Color.BLACK)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER

                        setLayerType(
                            if (useSoftwareLayer) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE,
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
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        setOnTouchListener { v, event ->
                            if (!latestTouch) {
                                true
                            } else {
                                if (event.action == MotionEvent.ACTION_DOWN) {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                                false
                            }
                        }

                        webChromeClient = WebChromeClient()
                        webViewClient = LocalWallpaperWebClient(
                            context = context.applicationContext,
                            repository = repository,
                            inlinePreviewProvider = { latestInlineHtml },
                            onPageReady = { loaded ->
                                pageLoaded = true
                                pushAudioState(loaded, isResumed)
                                if (viewMode != null) {
                                    runCatching {
                                        loaded.evaluateJavascript(AudioBridgeScript.buildViewModeJs(viewMode), null)
                                    }
                                }
                                loaded.invalidate()
                            },
                            onRendererCrash = { crashedView, _ ->
                                safelyDestroyWebView(crashedView ?: webViewRef)
                                latestOnCrash()
                            }
                        )

                        onResume()
                        resumeTimers()
                        lastLoadedSignature = currentSignature
                        pageLoaded = false
                        loadUrl(url)
                        webViewRef = this
                    }
                } catch (_: Throwable) {
                    pageLoaded = true
                    latestOnCrash()
                    View(ctx).apply { setBackgroundColor(Color.BLACK) }
                }
            },
            update = { view ->
                if (view is WebView && webViewRef == null && !isDestroyed) {
                    webViewRef = view
                }
            }
        )

        if (!pageLoaded) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private const val MAX_RENDERER_RECOVERIES = 2

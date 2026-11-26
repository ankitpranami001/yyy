package com.example.ui.components

import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun NativeVideoPreview(
    videoFile: File,
    soundEnabled: Boolean,
    volume: Float,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSound by rememberUpdatedState(soundEnabled)
    val latestVolume by rememberUpdatedState(volume)

    var mediaPlayerRef by remember(videoFile.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var surfaceRef by remember(videoFile.absolutePath) { mutableStateOf<Surface?>(null) }
    var isPrepared by remember(videoFile.absolutePath) { mutableStateOf(false) }
    var loadError by remember(videoFile.absolutePath) { mutableStateOf(false) }

    fun updateVolume(mp: MediaPlayer?) {
        val vol = if (latestSound) latestVolume.coerceIn(0f, 1f) else 0f
        runCatching { mp?.setVolume(vol, vol) }
    }

    fun applyCenterCrop(textureView: TextureView, videoW: Int, videoH: Int) {
        val viewW = textureView.width.toFloat()
        val viewH = textureView.height.toFloat()
        if (viewW <= 0f || viewH <= 0f || videoW <= 0 || videoH <= 0) return

        val scaleX = videoW.toFloat() / viewW
        val scaleY = videoH.toFloat() / viewH
        val maxScale = maxOf(1f / scaleX, 1f / scaleY)
        val sx = scaleX * maxScale
        val sy = scaleY * maxScale

        val matrix = Matrix().apply {
            setScale(sx, sy, viewW / 2f, viewH / 2f)
        }
        textureView.setTransform(matrix)
    }

    fun releasePlayer() {
        isPrepared = false
        runCatching {
            mediaPlayerRef?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        }
        mediaPlayerRef = null
        runCatching { surfaceRef?.release() }
        surfaceRef = null
    }

    fun startPlayback(textureView: TextureView, st: SurfaceTexture) {
        releasePlayer()
        loadError = false
        val surface = Surface(st)
        surfaceRef = surface
        runCatching {
            val mp = MediaPlayer().apply {
                setSurface(surface)
                setDataSource(videoFile.absolutePath)
                isLooping = true
                setOnVideoSizeChangedListener { _, vw, vh ->
                    textureView.post { applyCenterCrop(textureView, vw, vh) }
                }
                setOnPreparedListener { player ->
                    isPrepared = true
                    textureView.post {
                        applyCenterCrop(textureView, player.videoWidth, player.videoHeight)
                    }
                    updateVolume(player)
                    runCatching { player.start() }
                }
                setOnCompletionListener { player ->
                    runCatching {
                        player.seekTo(0)
                        player.start()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    loadError = true
                    true
                }
                prepareAsync()
            }
            mediaPlayerRef = mp
        }.onFailure {
            loadError = true
        }
    }

    LaunchedEffect(soundEnabled, volume, mediaPlayerRef, isPrepared) {
        if (isPrepared) {
            updateVolume(mediaPlayerRef)
        }
    }

    DisposableEffect(lifecycleOwner, videoFile.absolutePath) {
        val observer = LifecycleEventObserver { _, event ->
            val mp = mediaPlayerRef ?: return@LifecycleEventObserver
            if (!isPrepared) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching {
                    updateVolume(mp)
                    if (!mp.isPlaying) mp.start()
                }
                Lifecycle.Event.ON_PAUSE -> runCatching {
                    if (mp.isPlaying) mp.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    val textureView = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            startPlayback(textureView, st)
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                            val mp = mediaPlayerRef
                            if (mp != null && isPrepared) {
                                textureView.post { applyCenterCrop(textureView, mp.videoWidth, mp.videoHeight) }
                            }
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            releasePlayer()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                    if (isAvailable && surfaceTexture != null) {
                        surfaceTexture?.let { startPlayback(textureView, it) }
                    }
                }
            }
        )

        if (!isPrepared && !loadError) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (loadError) {
            Text(
                text = "Unsupported video format",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun NativeGifPreview(
    gifFile: File,
    fallbackUrl: String,
    repository: WallpaperRepository,
    reloadKey: Any,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val lifecycleOwner = LocalLifecycleOwner.current
        var drawable by remember(gifFile.absolutePath, reloadKey) { mutableStateOf<Drawable?>(null) }
        var decodeFinished by remember(gifFile.absolutePath, reloadKey) { mutableStateOf(false) }

        LaunchedEffect(gifFile.absolutePath, reloadKey) {
            decodeFinished = false
            drawable = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(gifFile)
                    ImageDecoder.decodeDrawable(source)
                }.getOrNull()
            }
            decodeFinished = true
        }

        DisposableEffect(lifecycleOwner, drawable) {
            val current = drawable
            if (current is AnimatedImageDrawable) {
                current.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                current.start()
            }
            val observer = LifecycleEventObserver { _, event ->
                if (current is AnimatedImageDrawable) {
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> if (!current.isRunning) current.start()
                        Lifecycle.Event.ON_PAUSE -> if (current.isRunning) current.stop()
                        else -> Unit
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (current is AnimatedImageDrawable) {
                    current.stop()
                }
            }
        }

        if (decodeFinished && drawable == null) {
            LiveWebPreview(
                url = fallbackUrl,
                repository = repository,
                soundEnabled = false,
                volume = 0f,
                touchEnabled = false,
                reloadKey = reloadKey,
                preferSoftwareLayer = true,
                modifier = modifier
            )
            return
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val loaded = drawable
            if (loaded != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setImageDrawable(loaded)
                        }
                    },
                    update = { iv ->
                        if (iv.drawable !== loaded) {
                            iv.setImageDrawable(loaded)
                        }
                        if (loaded is AnimatedImageDrawable && !loaded.isRunning) {
                            loaded.start()
                        }
                    }
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        LiveWebPreview(
            url = fallbackUrl,
            repository = repository,
            soundEnabled = false,
            volume = 0f,
            touchEnabled = false,
            reloadKey = reloadKey,
            preferSoftwareLayer = true,
            modifier = modifier
        )
    }
}

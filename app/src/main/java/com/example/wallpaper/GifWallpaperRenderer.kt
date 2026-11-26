package com.example.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Native Canvas-based GIF renderer for HtmlLiveWallpaperService.
 * Avoids WebView and VirtualDisplay entirely for GIF wallpapers, eliminating blank black screens
 * and Chromium renderer overhead on all Android 7–16 devices.
 */
class GifWallpaperRenderer {

    private var drawable: Drawable? = null
    @Suppress("DEPRECATION")
    private var movie: Movie? = null
    private var staticBitmap: Bitmap? = null
    private var movieStartMs: Long = 0L
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val isLoaded: Boolean
        get() = drawable != null || movie != null || staticBitmap != null

    fun load(file: File): Boolean {
        release()
        if (!file.exists() || !file.isFile || file.length() <= 0L) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeDrawable(source)
            }.getOrNull()
            if (decoded != null) {
                drawable = decoded
                if (decoded is AnimatedImageDrawable) {
                    decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    decoded.start()
                }
                return true
            }
        }

        @Suppress("DEPRECATION")
        val decodedMovie = runCatching {
            FileInputStream(file).use { Movie.decodeStream(it) }
        }.getOrNull()
        if (decodedMovie != null && decodedMovie.width() > 0 && decodedMovie.height() > 0) {
            movie = decodedMovie
            movieStartMs = SystemClock.uptimeMillis()
            return true
        }

        val fallbackBitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
        if (fallbackBitmap != null && fallbackBitmap.width > 0 && fallbackBitmap.height > 0) {
            staticBitmap = fallbackBitmap
            return true
        }

        return false
    }

    fun setPlaying(playing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val anim = drawable as? AnimatedImageDrawable ?: return
            runCatching {
                if (playing) {
                    if (!anim.isRunning) anim.start()
                } else {
                    if (anim.isRunning) anim.stop()
                }
            }
        }
    }

    fun drawFrame(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        canvas.drawColor(Color.BLACK)

        val currentDrawable = drawable
        if (currentDrawable != null) {
            val intrinsicW = currentDrawable.intrinsicWidth.takeIf { it > 0 } ?: width
            val intrinsicH = currentDrawable.intrinsicHeight.takeIf { it > 0 } ?: height
            val scale = max(width.toFloat() / intrinsicW, height.toFloat() / intrinsicH)
            val drawW = (intrinsicW * scale).roundToInt()
            val drawH = (intrinsicH * scale).roundToInt()
            val left = (width - drawW) / 2
            val top = (height - drawH) / 2
            currentDrawable.setBounds(left, top, left + drawW, top + drawH)
            currentDrawable.draw(canvas)
            return
        }

        @Suppress("DEPRECATION")
        val currentMovie = movie
        if (currentMovie != null) {
            val now = SystemClock.uptimeMillis()
            if (movieStartMs == 0L) movieStartMs = now
            val duration = currentMovie.duration().takeIf { it > 0 } ?: 1000
            val relTime = ((now - movieStartMs) % duration).toInt()
            currentMovie.setTime(relTime)

            val mw = currentMovie.width().toFloat()
            val mh = currentMovie.height().toFloat()
            val scale = max(width / mw, height / mh)
            val dx = (width - mw * scale) / 2f
            val dy = (height - mh * scale) / 2f
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            currentMovie.draw(canvas, 0f, 0f)
            canvas.restore()
            return
        }

        val bmp = staticBitmap ?: return
        val scale = max(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val drawW = (bmp.width * scale).roundToInt()
        val drawH = (bmp.height * scale).roundToInt()
        val left = (width - drawW) / 2
        val top = (height - drawH) / 2
        canvas.drawBitmap(bmp, null, Rect(left, top, left + drawW, top + drawH), bitmapPaint)
    }

    fun release() {
        setPlaying(false)
        drawable = null
        @Suppress("DEPRECATION")
        movie = null
        staticBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }
        staticBitmap = null
        movieStartMs = 0L
    }
}

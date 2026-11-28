package com.example.wallpaper

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.WallpaperRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile

class LocalWallpaperWebClient(
    private val context: Context,
    private val repository: WallpaperRepository,
    private val inlinePreviewProvider: (() -> String?)? = null,
    private val onPageReady: ((WebView) -> Unit)? = null,
    private val onRendererCrash: ((WebView?, Boolean) -> Unit)? = null
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return null
        if (!WallpaperRepository.VIRTUAL_HOST.equals(uri.host, ignoreCase = true)) {
            return null
        }

        val path = uri.path?.trimStart('/') ?: return notFoundResponse()
        if (path == "preview/live") {
            val html = inlinePreviewProvider?.invoke() ?: ""
            val injected = AudioBridgeScript.injectIntoHtml(html)
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                corsHeaders(),
                ByteArrayInputStream(injected.toByteArray(Charsets.UTF_8))
            )
        }

        if (path.startsWith("builtin/")) {
            val assetRelPath = path.removePrefix("builtin/")
            return serveAsset("wallpapers/$assetRelPath")
        }

        if (path.startsWith("user/")) {
            val userRelPath = path.removePrefix("user/")
            val slashIdx = userRelPath.indexOf('/')
            if (slashIdx <= 0) return notFoundResponse()
            val id = userRelPath.substring(0, slashIdx)
            val filePath = userRelPath.substring(slashIdx + 1)
            val baseDir = repository.getUserWallpaperDir(id)
            return serveUserFile(baseDir, filePath, request.requestHeaders["Range"], isThemeOverlay = false)
        }

        if (path.startsWith("theme/")) {
            val themeRelPath = path.removePrefix("theme/")
            val slashIdx = themeRelPath.indexOf('/')
            if (slashIdx <= 0) return notFoundResponse()
            val id = themeRelPath.substring(0, slashIdx)
            val filePath = themeRelPath.substring(slashIdx + 1)
            val baseDir = repository.getUserThemeDir(id)
            return serveUserFile(baseDir, filePath, request.requestHeaders["Range"], isThemeOverlay = true)
        }

        return notFoundResponse()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null) {
            onPageReady?.invoke(view)
        }
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?
    ): Boolean {
        val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            detail?.didCrash() == true
        } else {
            true
        }
        Log.w(TAG, "WebView render process gone (didCrash=$didCrash). Handling gracefully.")
        onRendererCrash?.invoke(view, didCrash)
        // Return true to prevent Chromium from terminating the host application process.
        return true
    }

    private fun serveAsset(assetPath: String): WebResourceResponse {
        return runCatching {
            val mime = guessMimeType(assetPath)
            val stream = context.assets.open(assetPath)
            if (mime == "text/html") {
                val html = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val injected = AudioBridgeScript.injectIntoHtml(html)
                WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    corsHeaders(),
                    ByteArrayInputStream(injected.toByteArray(Charsets.UTF_8))
                )
            } else {
                WebResourceResponse(
                    mime,
                    if (isTextMime(mime)) "UTF-8" else null,
                    200,
                    "OK",
                    corsHeaders(),
                    stream
                )
            }
        }.getOrElse { notFoundResponse() }
    }

    private fun serveUserFile(
        baseDir: File,
        relativePath: String,
        rangeHeader: String?,
        isThemeOverlay: Boolean
    ): WebResourceResponse {
        return runCatching {
            val canonicalBase = baseDir.canonicalPath + File.separator
            val target = File(baseDir, relativePath)
            if (!target.canonicalPath.startsWith(canonicalBase) || !target.exists() || !target.isFile) {
                return notFoundResponse()
            }

            val mime = guessMimeType(target.name)
            if (mime == "text/html") {
                val html = target.readText(Charsets.UTF_8)
                val injected = if (isThemeOverlay) {
                    val forceTransparent = repository.readSettings().themeTransparentBackground
                    AudioBridgeScript.injectThemeIntoHtml(html, forceTransparentRoot = forceTransparent)
                } else {
                    AudioBridgeScript.injectIntoHtml(html)
                }
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    corsHeaders(),
                    ByteArrayInputStream(injected.toByteArray(Charsets.UTF_8))
                )
            }

            val totalLength = target.length()
            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                val spec = rangeHeader.removePrefix("bytes=").substringBefore(',')
                val parts = spec.split('-')
                val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val end = parts.getOrNull(1)?.toLongOrNull()?.coerceAtMost(totalLength - 1)
                    ?: (totalLength - 1)
                if (start in 0..end && end < totalLength) {
                    val contentLength = end - start + 1
                    val headers = corsHeaders().toMutableMap().apply {
                        put("Accept-Ranges", "bytes")
                        put("Content-Range", "bytes $start-$end/$totalLength")
                        put("Content-Length", contentLength.toString())
                    }
                    return WebResourceResponse(
                        mime,
                        null,
                        206,
                        "Partial Content",
                        headers,
                        BoundedFileInputStream(target, start, contentLength)
                    )
                }
            }

            val headers = corsHeaders().toMutableMap().apply {
                put("Accept-Ranges", "bytes")
                put("Content-Length", totalLength.toString())
            }
            WebResourceResponse(
                mime,
                if (isTextMime(mime)) "UTF-8" else null,
                200,
                "OK",
                headers,
                FileInputStream(target)
            )
        }.getOrElse { notFoundResponse() }
    }

    private fun corsHeaders(): Map<String, String> = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Cache-Control" to "no-cache"
    )

    private fun notFoundResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        404,
        "Not Found",
        corsHeaders(),
        ByteArrayInputStream("Not Found".toByteArray())
    )

    private fun isTextMime(mime: String): Boolean =
        mime.startsWith("text/") || mime == "application/javascript" || mime == "application/json" || mime == "image/svg+xml"

    private fun guessMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "wasm" -> "application/wasm"
            else -> "application/octet-stream"
        }
    }

    private class BoundedFileInputStream(
        file: File,
        startOffset: Long,
        private var remaining: Long
    ) : InputStream() {
        private val raf = RandomAccessFile(file, "r").apply { seek(startOffset) }

        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = raf.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val count = raf.read(b, off, toRead)
            if (count > 0) remaining -= count
            return count
        }

        override fun close() {
            raf.close()
        }
    }

    companion object {
        private const val TAG = "LocalWallpaperClient"
    }
}

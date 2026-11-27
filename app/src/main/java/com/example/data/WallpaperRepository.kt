package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class WallpaperRepository(private val appContext: Context) {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val wallpapersRootDir: File =
        File(appContext.filesDir, WALLPAPERS_DIR).apply { mkdirs() }

    private val _wallpapers = MutableStateFlow<List<WallpaperItem>>(emptyList())
    val wallpapers: StateFlow<List<WallpaperItem>> = _wallpapers.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<WallpaperSettings> = _settings.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
        _wallpapers.value = loadAllWallpapers()
    }

    init {
        _wallpapers.value = loadAllWallpapers()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun getPrefs(): SharedPreferences = prefs

    fun getBuiltInWallpapers(): List<WallpaperItem> = listOf(
        WallpaperItem(
            id = WallpaperSettings.BUILTIN_AURORA_ID,
            name = "Aurora Pulse",
            entryFile = "index.html",
            source = WallpaperSource.BUILTIN,
            sizeBytes = 6400L,
            createdAt = 1L,
            accentHex = "#0EA5E9",
            description = "Default live wallpaper"
        )
    )

    fun getWallpaperById(id: String): WallpaperItem {
        return loadAllWallpapers().firstOrNull { it.id == id } ?: getBuiltInWallpapers().first()
    }

    fun getActiveWallpaper(): WallpaperItem {
        return getWallpaperById(readSettings().activeWallpaperId)
    }

    fun resolveWallpaperUrl(item: WallpaperItem): String {
        val cleanEntry = item.entryFile.trimStart('/')
        return if (item.isBuiltIn) {
            val folder = builtInAssetFolder(item.id)
            "https://$VIRTUAL_HOST/builtin/$folder/$cleanEntry"
        } else {
            "https://$VIRTUAL_HOST/user/${item.id}/$cleanEntry"
        }
    }

    fun getMediaFile(item: WallpaperItem): File? {
        if (item.isBuiltIn) return null
        val dir = getUserWallpaperDir(item.id)
        return when (item.source) {
            WallpaperSource.UPLOADED_VIDEO -> File(dir, item.entryFile).takeIf { it.exists() }
            WallpaperSource.UPLOADED_GIF -> File(dir, GIF_FILE_NAME).takeIf { it.exists() }
            else -> File(dir, item.entryFile).takeIf { it.exists() }
        }
    }

    fun builtInAssetFolder(id: String): String = "aurora_pulse"

    fun getUserWallpaperDir(id: String): File = File(wallpapersRootDir, id)

    fun readSettings(): WallpaperSettings {
        val modeName = prefs.getString(KEY_RENDER_MODE, RenderEngineMode.COMPATIBILITY_CANVAS.name)
        val renderMode = runCatching { RenderEngineMode.valueOf(modeName ?: "") }
            .getOrDefault(RenderEngineMode.COMPATIBILITY_CANVAS)
        val targetName = prefs.getString(KEY_WALLPAPER_TARGET, WallpaperTarget.BOTH.name)
        val wallpaperTarget = runCatching { WallpaperTarget.valueOf(targetName ?: "") }
            .getOrDefault(WallpaperTarget.BOTH)
        return WallpaperSettings(
            activeWallpaperId = prefs.getString(
                KEY_ACTIVE_ID,
                WallpaperSettings.BUILTIN_AURORA_ID
            ) ?: WallpaperSettings.BUILTIN_AURORA_ID,
            wallpaperTarget = wallpaperTarget,
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            volume = prefs.getFloat(KEY_VOLUME, 0.75f).coerceIn(0f, 1f),
            muteDuringOtherMedia = prefs.getBoolean(KEY_MUTE_OTHER_MEDIA, true),
            touchEnabled = prefs.getBoolean(KEY_TOUCH_ENABLED, true),
            targetFps = prefs.getInt(KEY_TARGET_FPS, 30).coerceIn(15, 60),
            renderMode = renderMode,
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, false)
        )
    }

    fun updateSettings(transform: (WallpaperSettings) -> WallpaperSettings) {
        val updated = transform(readSettings())
        prefs.edit()
            .putString(KEY_ACTIVE_ID, updated.activeWallpaperId)
            .putString(KEY_WALLPAPER_TARGET, updated.wallpaperTarget.name)
            .putBoolean(KEY_SOUND_ENABLED, updated.soundEnabled)
            .putFloat(KEY_VOLUME, updated.volume.coerceIn(0f, 1f))
            .putBoolean(KEY_MUTE_OTHER_MEDIA, updated.muteDuringOtherMedia)
            .putBoolean(KEY_TOUCH_ENABLED, updated.touchEnabled)
            .putInt(KEY_TARGET_FPS, updated.targetFps.coerceIn(15, 60))
            .putString(KEY_RENDER_MODE, updated.renderMode.name)
            .putBoolean(KEY_DARK_THEME, updated.darkTheme)
            .putLong(KEY_REVISION, System.currentTimeMillis())
            .apply()
        _settings.value = updated
    }

    fun selectWallpaper(id: String) {
        updateSettings { it.copy(activeWallpaperId = id) }
    }

    suspend fun importHtmlOrZip(uri: Uri): Result<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "wallpaper_${System.currentTimeMillis()}.html"
            val lowerName = rawName.lowercase()
            val id = "html_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val targetDir = File(wallpapersRootDir, id).apply { mkdirs() }

            val item = if (isZipUri(uri, lowerName)) {
                extractZipBundle(uri, rawName, id, targetDir)
            } else {
                importSingleHtml(uri, rawName, id, targetDir)
            }
            registerImportedItem(item)
        }
    }

    private fun isZipUri(uri: Uri, lowerName: String): Boolean {
        if (lowerName.endsWith(".zip")) return true
        val mime = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()?.lowercase()
        if (mime == "application/zip" || mime == "application/x-zip-compressed" || mime == "multipart/x-zip") {
            return true
        }
        // Inspect first 4 magic bytes (PK\x03\x04 or empty archive PK\x05\x06) for OEM pickers that strip extensions
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read >= 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    ((header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                        (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()))
            } ?: false
        }.getOrDefault(false)
    }

    suspend fun importVideo(uri: Uri): Result<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
            val ext = rawName.substringAfterLast('.', "mp4")
                .lowercase()
                .filter { it.isLetterOrDigit() }
                .ifBlank { "mp4" }
            val fileName = "video.$ext"
            val id = "vid_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val targetDir = File(wallpapersRootDir, id).apply { mkdirs() }
            val destFile = File(targetDir, fileName)

            val totalBytes = copyUriToFile(uri, destFile, MAX_VIDEO_BYTES, targetDir)
            val cleanName = cleanDisplayName(rawName, "Video Wallpaper")

            val item = WallpaperItem(
                id = id,
                name = cleanName,
                entryFile = fileName,
                source = WallpaperSource.UPLOADED_VIDEO,
                sizeBytes = totalBytes,
                createdAt = System.currentTimeMillis(),
                accentHex = "#8B5CF6",
                description = rawName
            )
            registerImportedItem(item)
        }
    }

    suspend fun importGif(uri: Uri): Result<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(uri) ?: "animation_${System.currentTimeMillis()}.gif"
            val id = "gif_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val targetDir = File(wallpapersRootDir, id).apply { mkdirs() }
            val gifFile = File(targetDir, GIF_FILE_NAME)

            val totalBytes = copyUriToFile(uri, gifFile, MAX_IMPORT_BYTES, targetDir)

            // Generate lightweight full-bleed HTML wrapper for Live Wallpaper WebView engine
            val wrapperHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width,initial-scale=1.0,user-scalable=no">
                <style>
                  * { margin: 0; padding: 0; box-sizing: border-box; }
                  html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                  img { width: 100%; height: 100%; object-fit: cover; display: block; }
                </style>
                </head>
                <body><img src="$GIF_FILE_NAME" alt="GIF Wallpaper"/></body>
                </html>
            """.trimIndent()
            File(targetDir, "index.html").writeText(wrapperHtml, Charsets.UTF_8)

            val cleanName = cleanDisplayName(rawName, "GIF Wallpaper")
            val item = WallpaperItem(
                id = id,
                name = cleanName,
                entryFile = "index.html",
                source = WallpaperSource.UPLOADED_GIF,
                sizeBytes = totalBytes,
                createdAt = System.currentTimeMillis(),
                accentHex = "#F97316",
                description = rawName
            )
            registerImportedItem(item)
        }
    }

    private fun registerImportedItem(item: WallpaperItem): WallpaperItem {
        val existing = readUserWallpapers().toMutableList()
        // Keep storage tidy (keep latest 10 uploads)
        while (existing.size >= 10) {
            val removed = existing.removeAt(existing.lastIndex)
            File(wallpapersRootDir, removed.id).deleteRecursively()
        }
        existing.add(0, item)
        saveUserWallpapers(existing)
        selectWallpaper(item.id)
        _wallpapers.value = loadAllWallpapers()
        return item
    }

    private fun copyUriToFile(
        uri: Uri,
        destFile: File,
        maxBytes: Long,
        cleanupDir: File
    ): Long {
        var totalBytes = 0L
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    totalBytes += read
                    if (totalBytes > maxBytes) {
                        cleanupDir.deleteRecursively()
                        throw IllegalArgumentException("File size exceeds allowed limit")
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: run {
            cleanupDir.deleteRecursively()
            throw IllegalStateException("Cannot open selected file")
        }

        if (totalBytes == 0L) {
            cleanupDir.deleteRecursively()
            throw IllegalArgumentException("Selected file is empty")
        }
        return totalBytes
    }

    private fun importSingleHtml(
        uri: Uri,
        rawName: String,
        id: String,
        targetDir: File
    ): WallpaperItem {
        val destFile = File(targetDir, "index.html")
        val totalBytes = copyUriToFile(uri, destFile, MAX_IMPORT_BYTES, targetDir)

        return WallpaperItem(
            id = id,
            name = cleanDisplayName(rawName, "HTML Wallpaper"),
            entryFile = "index.html",
            source = WallpaperSource.UPLOADED_HTML,
            sizeBytes = totalBytes,
            createdAt = System.currentTimeMillis(),
            accentHex = "#0EA5E9",
            description = rawName
        )
    }

    private fun extractZipBundle(
        uri: Uri,
        rawName: String,
        id: String,
        targetDir: File
    ): WallpaperItem {
        val canonicalRoot = targetDir.canonicalPath + File.separator
        var totalBytes = 0L
        var fileCount = 0
        val htmlCandidates = mutableListOf<String>()

        appContext.contentResolver.openInputStream(uri)?.use { rawIn ->
            ZipInputStream(BufferedInputStream(rawIn)).use { zis ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val entryName = entry.name.replace('\\', '/')
                    if (entryName.startsWith("__MACOSX/") || entryName.contains("/._")) {
                        zis.closeEntry()
                        continue
                    }
                    val destFile = File(targetDir, entryName)
                    val canonicalDest = destFile.canonicalPath
                    if (!canonicalDest.startsWith(canonicalRoot)) {
                        targetDir.deleteRecursively()
                        throw SecurityException("Blocked unsafe path in ZIP archive")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        fileCount++
                        if (fileCount > MAX_ZIP_ENTRIES) {
                            targetDir.deleteRecursively()
                            throw IllegalArgumentException("ZIP contains too many files")
                        }
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            while (true) {
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                totalBytes += read
                                if (totalBytes > MAX_IMPORT_BYTES) {
                                    targetDir.deleteRecursively()
                                    throw IllegalArgumentException("ZIP exceeds 100 MB limit")
                                }
                                fos.write(buffer, 0, read)
                            }
                        }
                        val relPath = canonicalDest.removePrefix(canonicalRoot)
                            .replace(File.separatorChar, '/')
                        val low = relPath.lowercase()
                        if (low.endsWith(".html") || low.endsWith(".htm")) {
                            htmlCandidates.add(relPath)
                        }
                    }
                    zis.closeEntry()
                }
            }
        } ?: run {
            targetDir.deleteRecursively()
            throw IllegalStateException("Cannot open selected ZIP archive")
        }

        if (htmlCandidates.isEmpty()) {
            targetDir.deleteRecursively()
            throw IllegalArgumentException("No .html file found inside ZIP archive")
        }

        val chosenEntry = htmlCandidates.sortedWith(
            compareBy<String>(
                { !it.substringAfterLast('/').equals("index.html", ignoreCase = true) },
                { it.count { ch -> ch == '/' } },
                { it.length }
            )
        ).first()

        return WallpaperItem(
            id = id,
            name = cleanDisplayName(rawName, "HTML Bundle"),
            entryFile = chosenEntry,
            source = WallpaperSource.UPLOADED_ZIP,
            sizeBytes = totalBytes,
            createdAt = System.currentTimeMillis(),
            accentHex = "#10B981",
            description = "$fileCount files • $chosenEntry"
        )
    }

    private fun cleanDisplayName(rawName: String, fallback: String): String {
        return rawName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { fallback }
            .take(36)
    }

    private fun loadAllWallpapers(): List<WallpaperItem> {
        return readUserWallpapers() + getBuiltInWallpapers()
    }

    private fun readUserWallpapers(): List<WallpaperItem> {
        val raw = prefs.getString(KEY_USER_WALLPAPERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getString("id")
                    val entry = obj.optString("entryFile", "index.html")
                    val entryFileObj = File(File(wallpapersRootDir, id), entry)
                    if (!entryFileObj.exists()) continue
                    add(
                        WallpaperItem(
                            id = id,
                            name = obj.optString("name", "Live Wallpaper"),
                            entryFile = entry,
                            source = runCatching {
                                WallpaperSource.valueOf(
                                    obj.optString("source", WallpaperSource.UPLOADED_HTML.name)
                                )
                            }.getOrDefault(WallpaperSource.UPLOADED_HTML),
                            sizeBytes = obj.optLong("sizeBytes", entryFileObj.length()),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            accentHex = obj.optString("accentHex", "#0EA5E9"),
                            description = obj.optString("description", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveUserWallpapers(list: List<WallpaperItem>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("entryFile", item.entryFile)
            obj.put("source", item.source.name)
            obj.put("sizeBytes", item.sizeBytes)
            obj.put("createdAt", item.createdAt)
            obj.put("accentHex", item.accentHex)
            obj.put("description", item.description)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_USER_WALLPAPERS, arr.toString()).apply()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val VIRTUAL_HOST = "wallpaper.local"
        const val PREFS_NAME = "livehtml_prefs"
        private const val WALLPAPERS_DIR = "wallpapers"
        private const val GIF_FILE_NAME = "wallpaper.gif"
        private const val MAX_IMPORT_BYTES = 100L * 1024L * 1024L
        private const val MAX_VIDEO_BYTES = 250L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 600

        private const val KEY_USER_WALLPAPERS = "user_wallpapers_json"
        private const val KEY_ACTIVE_ID = "active_wallpaper_id"
        private const val KEY_WALLPAPER_TARGET = "wallpaper_target"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VOLUME = "sound_volume"
        private const val KEY_MUTE_OTHER_MEDIA = "mute_during_other_media"
        private const val KEY_TOUCH_ENABLED = "touch_enabled"
        private const val KEY_TARGET_FPS = "target_fps"
        private const val KEY_RENDER_MODE = "render_mode"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_REVISION = "content_revision"
    }
}

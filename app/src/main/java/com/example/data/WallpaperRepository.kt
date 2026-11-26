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
            accentHex = "#22D3EE",
            description = "Flowing aurora ribbons with touch ripples & crystal Web Audio chimes"
        ),
        WallpaperItem(
            id = WallpaperSettings.BUILTIN_CYBER_RAIN_ID,
            name = "Cyber Rain",
            entryFile = "index.html",
            source = WallpaperSource.BUILTIN,
            sizeBytes = 5800L,
            createdAt = 2L,
            accentHex = "#FF7A59",
            description = "Neon rainfall streaks, lightning taps & procedural rain ambiance"
        ),
        WallpaperItem(
            id = WallpaperSettings.BUILTIN_SOLAR_ORBIT_ID,
            name = "Solar Clock Orbit",
            entryFile = "index.html",
            source = WallpaperSource.BUILTIN,
            sizeBytes = 5200L,
            createdAt = 3L,
            accentHex = "#FACC15",
            description = "Live orbital clock with planetary rings & harmonic second ticks"
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

    fun builtInAssetFolder(id: String): String = when (id) {
        WallpaperSettings.BUILTIN_AURORA_ID -> "aurora_pulse"
        WallpaperSettings.BUILTIN_CYBER_RAIN_ID -> "cyber_rain"
        WallpaperSettings.BUILTIN_SOLAR_ORBIT_ID -> "solar_orbit"
        else -> "aurora_pulse"
    }

    fun getUserWallpaperDir(id: String): File = File(wallpapersRootDir, id)

    fun readSettings(): WallpaperSettings {
        val modeName = prefs.getString(KEY_RENDER_MODE, RenderEngineMode.AUTO_HARDWARE.name)
        val renderMode = runCatching { RenderEngineMode.valueOf(modeName ?: "") }
            .getOrDefault(RenderEngineMode.AUTO_HARDWARE)
        return WallpaperSettings(
            activeWallpaperId = prefs.getString(
                KEY_ACTIVE_ID,
                WallpaperSettings.BUILTIN_AURORA_ID
            ) ?: WallpaperSettings.BUILTIN_AURORA_ID,
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            volume = prefs.getFloat(KEY_VOLUME, 0.75f).coerceIn(0f, 1f),
            muteDuringOtherMedia = prefs.getBoolean(KEY_MUTE_OTHER_MEDIA, true),
            touchEnabled = prefs.getBoolean(KEY_TOUCH_ENABLED, true),
            targetFps = prefs.getInt(KEY_TARGET_FPS, 30).coerceIn(15, 60),
            renderMode = renderMode
        )
    }

    fun updateSettings(transform: (WallpaperSettings) -> WallpaperSettings) {
        val updated = transform(readSettings())
        prefs.edit()
            .putString(KEY_ACTIVE_ID, updated.activeWallpaperId)
            .putBoolean(KEY_SOUND_ENABLED, updated.soundEnabled)
            .putFloat(KEY_VOLUME, updated.volume.coerceIn(0f, 1f))
            .putBoolean(KEY_MUTE_OTHER_MEDIA, updated.muteDuringOtherMedia)
            .putBoolean(KEY_TOUCH_ENABLED, updated.touchEnabled)
            .putInt(KEY_TARGET_FPS, updated.targetFps.coerceIn(15, 60))
            .putString(KEY_RENDER_MODE, updated.renderMode.name)
            .putLong(KEY_REVISION, System.currentTimeMillis())
            .apply()
        _settings.value = updated
    }

    fun selectWallpaper(id: String) {
        updateSettings { it.copy(activeWallpaperId = id) }
    }

    fun notifyContentModified() {
        prefs.edit().putLong(KEY_REVISION, System.currentTimeMillis()).apply()
    }

    suspend fun importFromUri(uri: Uri): Result<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val rawName = queryDisplayName(uri) ?: "wallpaper_${System.currentTimeMillis()}.html"
            val lowerName = rawName.lowercase()
            val id = "user_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val targetDir = File(wallpapersRootDir, id).apply { mkdirs() }

            val item = if (lowerName.endsWith(".zip")) {
                extractZipBundle(uri, rawName, id, targetDir)
            } else {
                importSingleHtml(uri, rawName, id, targetDir)
            }

            val existing = readUserWallpapers().toMutableList()
            existing.add(0, item)
            saveUserWallpapers(existing)
            selectWallpaper(item.id)
            _wallpapers.value = loadAllWallpapers()
            item
        }.onFailure {
            // Cleanup handled inside helpers on failure
        }
    }

    private fun importSingleHtml(
        uri: Uri,
        rawName: String,
        id: String,
        targetDir: File
    ): WallpaperItem {
        val destFile = File(targetDir, "index.html")
        var totalBytes = 0L
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    totalBytes += read
                    if (totalBytes > MAX_IMPORT_BYTES) {
                        targetDir.deleteRecursively()
                        throw IllegalArgumentException("HTML file exceeds 100 MB limit")
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: run {
            targetDir.deleteRecursively()
            throw IllegalStateException("Cannot open selected file")
        }

        if (totalBytes == 0L) {
            targetDir.deleteRecursively()
            throw IllegalArgumentException("Selected file is empty")
        }

        val cleanName = rawName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Custom HTML" }

        return WallpaperItem(
            id = id,
            name = cleanName.take(40),
            entryFile = "index.html",
            source = WallpaperSource.UPLOADED_HTML,
            sizeBytes = totalBytes,
            createdAt = System.currentTimeMillis(),
            accentHex = pickAccentForId(id),
            description = "Uploaded HTML file ($rawName)"
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
                    val entryName = entry.name
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
            throw IllegalArgumentException("No .html file found inside ZIP bundle")
        }

        // Prefer shallowest index.html, otherwise shallowest .html file
        val chosenEntry = htmlCandidates.sortedWith(
            compareBy<String>(
                { !it.substringAfterLast('/').equals("index.html", ignoreCase = true) },
                { it.count { ch -> ch == '/' } },
                { it.length }
            )
        ).first()

        val cleanName = rawName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "HTML Bundle" }

        return WallpaperItem(
            id = id,
            name = cleanName.take(40),
            entryFile = chosenEntry,
            source = WallpaperSource.UPLOADED_ZIP,
            sizeBytes = totalBytes,
            createdAt = System.currentTimeMillis(),
            accentHex = pickAccentForId(id),
            description = "ZIP bundle ($fileCount files • $chosenEntry)"
        )
    }

    suspend fun saveCustomHtml(
        name: String,
        htmlContent: String,
        existingId: String? = null
    ): Result<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedName = name.trim().ifBlank { "Custom Creation" }.take(40)
            val existingList = readUserWallpapers().toMutableList()
            val existingIdx = existingId?.let { id -> existingList.indexOfFirst { it.id == id } } ?: -1

            val item = if (existingIdx >= 0) {
                val old = existingList[existingIdx]
                val targetFile = File(File(wallpapersRootDir, old.id), old.entryFile)
                targetFile.parentFile?.mkdirs()
                targetFile.writeText(htmlContent, Charsets.UTF_8)
                val updated = old.copy(
                    name = trimmedName,
                    sizeBytes = targetFile.length(),
                    createdAt = System.currentTimeMillis()
                )
                existingList[existingIdx] = updated
                updated
            } else {
                val id = "code_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                val dir = File(wallpapersRootDir, id).apply { mkdirs() }
                val targetFile = File(dir, "index.html")
                targetFile.writeText(htmlContent, Charsets.UTF_8)
                val created = WallpaperItem(
                    id = id,
                    name = trimmedName,
                    entryFile = "index.html",
                    source = WallpaperSource.CUSTOM_CODE,
                    sizeBytes = targetFile.length(),
                    createdAt = System.currentTimeMillis(),
                    accentHex = pickAccentForId(id),
                    description = "Created in Code Lab"
                )
                existingList.add(0, created)
                created
            }

            saveUserWallpapers(existingList)
            selectWallpaper(item.id)
            notifyContentModified()
            _wallpapers.value = loadAllWallpapers()
            item
        }
    }

    suspend fun readWallpaperHtml(item: WallpaperItem): String = withContext(Dispatchers.IO) {
        runCatching {
            if (item.isBuiltIn) {
                val folder = builtInAssetFolder(item.id)
                appContext.assets.open("wallpapers/$folder/${item.entryFile}")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val file = File(File(wallpapersRootDir, item.id), item.entryFile)
                if (file.exists()) file.readText(Charsets.UTF_8) else ""
            }
        }.getOrDefault("")
    }

    suspend fun deleteWallpaper(id: String): Boolean = withContext(Dispatchers.IO) {
        val current = readUserWallpapers().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            File(wallpapersRootDir, id).deleteRecursively()
            saveUserWallpapers(current)
            if (readSettings().activeWallpaperId == id) {
                val fallback = current.firstOrNull()?.id ?: WallpaperSettings.BUILTIN_AURORA_ID
                selectWallpaper(fallback)
            }
            _wallpapers.value = loadAllWallpapers()
        }
        removed
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
                            name = obj.optString("name", "HTML Wallpaper"),
                            entryFile = entry,
                            source = runCatching {
                                WallpaperSource.valueOf(obj.optString("source", WallpaperSource.UPLOADED_HTML.name))
                            }.getOrDefault(WallpaperSource.UPLOADED_HTML),
                            sizeBytes = obj.optLong("sizeBytes", entryFileObj.length()),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            accentHex = obj.optString("accentHex", "#22D3EE"),
                            description = obj.optString("description", "Uploaded HTML wallpaper")
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

    private fun pickAccentForId(id: String): String {
        val palette = listOf("#22D3EE", "#FF7A59", "#34D399", "#FACC15", "#38BDF8", "#FB7185")
        val index = (id.hashCode() and 0x7FFFFFFF) % palette.size
        return palette[index]
    }

    companion object {
        const val VIRTUAL_HOST = "wallpaper.local"
        const val PREFS_NAME = "livehtml_prefs"
        private const val WALLPAPERS_DIR = "wallpapers"
        private const val MAX_IMPORT_BYTES = 100L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 600

        private const val KEY_USER_WALLPAPERS = "user_wallpapers_json"
        private const val KEY_ACTIVE_ID = "active_wallpaper_id"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VOLUME = "sound_volume"
        private const val KEY_MUTE_OTHER_MEDIA = "mute_during_other_media"
        private const val KEY_TOUCH_ENABLED = "touch_enabled"
        private const val KEY_TARGET_FPS = "target_fps"
        private const val KEY_RENDER_MODE = "render_mode"
        private const val KEY_REVISION = "content_revision"
    }
}

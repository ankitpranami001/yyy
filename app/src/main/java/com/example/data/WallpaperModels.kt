package com.example.data

enum class WallpaperSource {
    BUILTIN,
    UPLOADED_HTML,
    UPLOADED_ZIP,
    UPLOADED_VIDEO,
    UPLOADED_GIF
}

enum class RenderEngineMode {
    AUTO_HARDWARE,
    COMPATIBILITY_CANVAS
}

data class WallpaperItem(
    val id: String,
    val name: String,
    val entryFile: String,
    val source: WallpaperSource,
    val sizeBytes: Long,
    val createdAt: Long,
    val accentHex: String,
    val description: String = ""
) {
    val isBuiltIn: Boolean
        get() = source == WallpaperSource.BUILTIN
}

data class WallpaperSettings(
    val activeWallpaperId: String = BUILTIN_AURORA_ID,
    val soundEnabled: Boolean = true,
    val volume: Float = 0.75f,
    val muteDuringOtherMedia: Boolean = true,
    val touchEnabled: Boolean = true,
    val targetFps: Int = 30,
    val renderMode: RenderEngineMode = RenderEngineMode.AUTO_HARDWARE,
    val darkTheme: Boolean = false
) {
    companion object {
        const val BUILTIN_AURORA_ID = "builtin_aurora_pulse"
    }
}

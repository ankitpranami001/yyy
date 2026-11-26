package com.example.wallpaper

import android.app.WallpaperManager
import android.os.Build
import com.example.data.WallpaperTarget

/**
 * Pure, deterministic placement policy for deciding when a live wallpaper engine should
 * display live content versus stay masked on the opposite screen (Home-Only / Lock-Only / Both).
 */
object WallpaperPlacementPolicy {

    /**
     * Returns true when the Android platform (API 34+) created a dedicated engine exclusively
     * for either the Home screen (FLAG_SYSTEM) or the Lock screen (FLAG_LOCK).
     */
    fun isDedicatedSingleScreenEngine(sdkInt: Int, wallpaperFlags: Int): Boolean {
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return wallpaperFlags == WallpaperManager.FLAG_SYSTEM ||
            wallpaperFlags == WallpaperManager.FLAG_LOCK
    }

    /**
     * Determines whether live wallpaper content should be rendered right now.
     * - System picker preview always displays live content.
     * - BOTH always displays live content.
     * - On Android 14+ with dedicated FLAG_SYSTEM / FLAG_LOCK engines, honors the engine flag directly.
     * - On shared engines (Android 7–13 or shared FLAG_SYSTEM | FLAG_LOCK), uses keyguard lock state.
     */
    fun shouldRenderLiveContent(
        isPreview: Boolean,
        target: WallpaperTarget,
        sdkInt: Int,
        wallpaperFlags: Int,
        isKeyguardLocked: Boolean
    ): Boolean {
        if (isPreview) return true
        if (target == WallpaperTarget.BOTH) return true

        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (wallpaperFlags == WallpaperManager.FLAG_SYSTEM) {
                return target == WallpaperTarget.HOME_ONLY
            }
            if (wallpaperFlags == WallpaperManager.FLAG_LOCK) {
                return target == WallpaperTarget.LOCK_ONLY
            }
        }

        return when (target) {
            WallpaperTarget.HOME_ONLY -> !isKeyguardLocked
            WallpaperTarget.LOCK_ONLY -> isKeyguardLocked
            WallpaperTarget.BOTH -> true
        }
    }

    /**
     * Returns true if this engine may need to dynamically switch between live and masked states
     * on the same Surface (shared engine with HOME_ONLY or LOCK_ONLY target).
     */
    fun needsDynamicScreenMasking(
        isPreview: Boolean,
        target: WallpaperTarget,
        sdkInt: Int,
        wallpaperFlags: Int
    ): Boolean {
        if (isPreview || target == WallpaperTarget.BOTH) return false
        return !isDedicatedSingleScreenEngine(sdkInt, wallpaperFlags)
    }
}

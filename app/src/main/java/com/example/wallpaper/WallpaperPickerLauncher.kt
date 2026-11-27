package com.example.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.R

/**
 * Universal multi-OEM Live Wallpaper picker launcher.
 * Handles Samsung One UI, Xiaomi MIUI/HyperOS, Vivo FuntouchOS, Oppo/Realme ColorOS,
 * OnePlus OxygenOS, Motorola, Tecno/Infinix, Huawei, and stock Android 7–16.
 */
object WallpaperPickerLauncher {

    enum class LaunchResult {
        OPENED_DIRECT,
        OPENED_CHOOSER,
        OPENED_GENERIC_WALLPAPER_SETTINGS,
        NOT_SUPPORTED_BY_OS,
        FAILED
    }

    fun buildCandidateIntents(context: Context): List<Pair<LaunchResult, Intent>> {
        val component = ComponentName(context, HtmlLiveWallpaperService::class.java)
        return listOf(
            // 1. Direct live wallpaper preview (Pixel, Samsung, OnePlus, Motorola, modern OEMs)
            LaunchResult.OPENED_DIRECT to Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            },
            // 2. Standard Live Wallpaper Chooser (older Samsung, Realme, Oppo, Vivo, LG, Sony)
            LaunchResult.OPENED_CHOOSER to Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
            // 3. Explicit AOSP LiveWallpaperPicker package (helpful on some MIUI / ColorOS / FuntouchOS builds)
            LaunchResult.OPENED_DIRECT to Intent().apply {
                setClassName(
                    "com.android.wallpaper.livepicker",
                    "com.android.wallpaper.livepicker.LiveWallpaperChange"
                )
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            },
            LaunchResult.OPENED_CHOOSER to Intent().apply {
                setClassName(
                    "com.android.wallpaper.livepicker",
                    "com.android.wallpaper.livepicker.LiveWallpaperActivity"
                )
            },
            // 4. Universal Android wallpaper chooser (MIUI Themes, Vivo iTheme, Oppo Theme Store, Huawei, Tecno)
            LaunchResult.OPENED_GENERIC_WALLPAPER_SETTINGS to Intent.createChooser(
                Intent(Intent.ACTION_SET_WALLPAPER),
                context.getString(R.string.chooser_select_wallpaper)
            )
        )
    }

    fun launch(activity: Activity): LaunchResult {
        val wm = runCatching { WallpaperManager.getInstance(activity) }.getOrNull()
        if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val supported = runCatching { wm.isWallpaperSupported }.getOrDefault(true)
            val allowed = runCatching { wm.isSetWallpaperAllowed }.getOrDefault(true)
            if (!supported || !allowed) {
                return LaunchResult.NOT_SUPPORTED_BY_OS
            }
        }

        val candidates = buildCandidateIntents(activity)
        for ((resultType, intent) in candidates) {
            val canAttempt = resultType == LaunchResult.OPENED_GENERIC_WALLPAPER_SETTINGS ||
                runCatching { intent.resolveActivity(activity.packageManager) != null }.getOrDefault(true)
            if (!canAttempt) continue

            val launched = runCatching {
                activity.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) {
                return resultType
            }
        }

        return LaunchResult.FAILED
    }
}

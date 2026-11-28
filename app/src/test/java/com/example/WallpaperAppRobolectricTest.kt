package com.example

import android.app.WallpaperManager
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.RenderEngineMode
import com.example.data.WallpaperRepository
import com.example.data.WallpaperSource
import com.example.data.WallpaperTarget
import com.example.ui.MainUiState
import com.example.ui.PhonePreviewStyle
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.LiveHtmlTheme
import com.example.wallpaper.AudioBridgeScript
import com.example.wallpaper.GifWallpaperRenderer
import com.example.wallpaper.LocalWallpaperWebClient
import com.example.wallpaper.WallpaperPickerLauncher
import com.example.wallpaper.WallpaperPlacementPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class WallpaperAppRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repository: WallpaperRepository
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(WallpaperRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "wallpapers").deleteRecursively()
        repository = WallpaperRepository(context)
    }

    @After
    fun tearDown() {
        repository.release()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
        File(context.filesDir, "wallpapers").deleteRecursively()
    }

    // -------------------------------------------------------------------------
    // 1. WallpaperPlacementPolicy Tests (Home-Only / Lock-Only / Both)
    // -------------------------------------------------------------------------

    @Test
    fun placementPolicy_previewAlwaysRenders() {
        for (target in WallpaperTarget.entries) {
            assertTrue(
                WallpaperPlacementPolicy.shouldRenderLiveContent(
                    isPreview = true,
                    target = target,
                    sdkInt = 34,
                    wallpaperFlags = WallpaperManager.FLAG_LOCK,
                    isKeyguardLocked = false
                )
            )
            assertFalse(
                WallpaperPlacementPolicy.needsDynamicScreenMasking(
                    isPreview = true,
                    target = target,
                    sdkInt = 33,
                    wallpaperFlags = 0
                )
            )
        }
    }

    @Test
    fun placementPolicy_bothAlwaysRenders() {
        assertTrue(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.BOTH,
                sdkInt = 34,
                wallpaperFlags = WallpaperManager.FLAG_SYSTEM,
                isKeyguardLocked = true
            )
        )
        assertTrue(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.BOTH,
                sdkInt = 33,
                wallpaperFlags = 0,
                isKeyguardLocked = false
            )
        )
    }

    @Test
    fun placementPolicy_android14DedicatedEngineFlags() {
        // HOME_ONLY on dedicated FLAG_SYSTEM -> true, on dedicated FLAG_LOCK -> false
        assertTrue(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.HOME_ONLY,
                sdkInt = 34,
                wallpaperFlags = WallpaperManager.FLAG_SYSTEM,
                isKeyguardLocked = true
            )
        )
        assertFalse(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.HOME_ONLY,
                sdkInt = 34,
                wallpaperFlags = WallpaperManager.FLAG_LOCK,
                isKeyguardLocked = false
            )
        )
        assertFalse(
            WallpaperPlacementPolicy.needsDynamicScreenMasking(
                isPreview = false,
                target = WallpaperTarget.HOME_ONLY,
                sdkInt = 34,
                wallpaperFlags = WallpaperManager.FLAG_SYSTEM
            )
        )

        // LOCK_ONLY on dedicated FLAG_LOCK -> true, on dedicated FLAG_SYSTEM -> false
        assertTrue(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.LOCK_ONLY,
                sdkInt = 34,
                wallpaperFlags = WallpaperManager.FLAG_LOCK,
                isKeyguardLocked = false
            )
        )
        assertFalse(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.LOCK_ONLY,
                sdkInt = 34,
                wallpaperFlags = WallpaperManager.FLAG_SYSTEM,
                isKeyguardLocked = true
            )
        )
    }

    @Test
    fun placementPolicy_sharedEngineUsesKeyguardState() {
        val sharedFlags = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK

        assertTrue(
            WallpaperPlacementPolicy.needsDynamicScreenMasking(
                isPreview = false,
                target = WallpaperTarget.HOME_ONLY,
                sdkInt = 34,
                wallpaperFlags = sharedFlags
            )
        )
        assertTrue(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.HOME_ONLY,
                sdkInt = 34,
                wallpaperFlags = sharedFlags,
                isKeyguardLocked = false
            )
        )
        assertFalse(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.HOME_ONLY,
                sdkInt = 34,
                wallpaperFlags = sharedFlags,
                isKeyguardLocked = true
            )
        )

        // API 33 LOCK_ONLY
        assertTrue(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.LOCK_ONLY,
                sdkInt = 33,
                wallpaperFlags = 0,
                isKeyguardLocked = true
            )
        )
        assertFalse(
            WallpaperPlacementPolicy.shouldRenderLiveContent(
                isPreview = false,
                target = WallpaperTarget.LOCK_ONLY,
                sdkInt = 33,
                wallpaperFlags = 0,
                isKeyguardLocked = false
            )
        )
    }

    // -------------------------------------------------------------------------
    // 2. WallpaperRepository Tests (HTML / ZIP / Video / GIF / Settings)
    // -------------------------------------------------------------------------

    @Test
    fun repository_importHtmlAndPersistSettings() = runBlocking {
        val htmlFile = createTempFile("neon_wave.html", "<html><head></head><body>Hi</body></html>".toByteArray())
        val result = repository.importHtmlOrZip(Uri.fromFile(htmlFile))
        assertTrue(result.isSuccess)
        val item = result.getOrThrow()

        assertEquals(WallpaperSource.UPLOADED_HTML, item.source)
        assertEquals("neon wave", item.name)
        assertEquals(item.id, repository.getActiveWallpaper().id)
        assertTrue(repository.resolveWallpaperUrl(item).startsWith("https://wallpaper.local/user/${item.id}/index.html"))

        repository.updateSettings {
            it.copy(
                wallpaperTarget = WallpaperTarget.LOCK_ONLY,
                soundEnabled = false,
                volume = 1.8f,
                darkTheme = true,
                renderMode = RenderEngineMode.COMPATIBILITY_CANVAS
            )
        }
        val persisted = repository.readSettings()
        assertEquals(WallpaperTarget.LOCK_ONLY, persisted.wallpaperTarget)
        assertFalse(persisted.soundEnabled)
        assertEquals(1.0f, persisted.volume, 0.001f)
        assertTrue(persisted.darkTheme)
        assertEquals(RenderEngineMode.COMPATIBILITY_CANVAS, persisted.renderMode)
    }

    @Test
    fun repository_importZipBundleAndBlockZipSlip() = runBlocking {
        // Valid ZIP with nested index.html + asset
        val validZip = createZipFile(
            "galaxy_pack.zip",
            mapOf(
                "scene/index.html" to "<!DOCTYPE html><html><head></head><body>Galaxy</body></html>".toByteArray(),
                "scene/style.css" to "body{background:#000;}".toByteArray()
            )
        )
        val validResult = repository.importHtmlOrZip(Uri.fromFile(validZip))
        assertTrue(validResult.isSuccess)
        val item = validResult.getOrThrow()
        assertEquals(WallpaperSource.UPLOADED_ZIP, item.source)
        assertEquals("scene/index.html", item.entryFile)

        // Malicious Zip-Slip archive must be rejected
        val maliciousZip = createZipFile(
            "bad.zip",
            mapOf("../../outside.html" to "<html>evil</html>".toByteArray())
        )
        val badResult = repository.importHtmlOrZip(Uri.fromFile(maliciousZip))
        assertTrue(badResult.isFailure)
        assertTrue(badResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun repository_detectsZipByMagicBytesAndHandlesWindowsPaths() = runBlocking {
        // Default render engine must be Universal Canvas for all-device OEM compatibility
        assertEquals(RenderEngineMode.COMPATIBILITY_CANVAS, repository.readSettings().renderMode)

        // Extensionless filename (common on Xiaomi/Vivo/Oppo file managers) + Windows backslash entry
        val noExtZip = createZipFile(
            "10004521",
            mapOf(
                "pack\\index.html" to "<html><head></head><body>WinZip</body></html>".toByteArray()
            )
        )
        val result = repository.importHtmlOrZip(Uri.fromFile(noExtZip))
        assertTrue(result.isSuccess)
        val item = result.getOrThrow()
        assertEquals(WallpaperSource.UPLOADED_ZIP, item.source)
        assertEquals("pack/index.html", item.entryFile)
    }

    @Test
    fun pickerLauncher_buildsMultiOemFallbackChain() {
        val candidates = WallpaperPickerLauncher.buildCandidateIntents(context)
        assertTrue(candidates.size >= 4)
        assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, candidates[0].second.action)
        assertEquals(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER, candidates[1].second.action)
        assertEquals(android.content.Intent.ACTION_CHOOSER, candidates.last().second.action)
    }

    @Test
    fun repository_importVideoAndGifFiles() = runBlocking {
        val fakeVideo = createTempFile("ocean_loop.mp4", ByteArray(1024) { it.toByte() })
        val videoRes = repository.importVideo(Uri.fromFile(fakeVideo))
        assertTrue(videoRes.isSuccess)
        val videoItem = videoRes.getOrThrow()
        assertEquals(WallpaperSource.UPLOADED_VIDEO, videoItem.source)
        val storedVideo = repository.getMediaFile(videoItem)
        assertNotNull(storedVideo)
        assertEquals(1024L, storedVideo!!.length())

        val gifBytes = minimalValidGifBytes()
        val fakeGif = createTempFile("sparkle.gif", gifBytes)
        val gifRes = repository.importGif(Uri.fromFile(fakeGif))
        assertTrue(gifRes.isSuccess)
        val gifItem = gifRes.getOrThrow()
        assertEquals(WallpaperSource.UPLOADED_GIF, gifItem.source)
        val storedGif = repository.getMediaFile(gifItem)
        assertNotNull(storedGif)
        assertEquals("wallpaper.gif", storedGif!!.name)
        assertTrue(File(repository.getUserWallpaperDir(gifItem.id), "index.html").exists())
    }

    // -------------------------------------------------------------------------
    // 3. LocalWallpaperWebClient, AudioBridgeScript & GifWallpaperRenderer Tests
    // -------------------------------------------------------------------------

    @Test
    fun webClient_servesBuiltinUploadedRangeAndBlocksTraversal() = runBlocking {
        val client = LocalWallpaperWebClient(
            context = context,
            repository = repository
        )

        // Built-in aurora_pulse
        val builtinResp = client.shouldInterceptRequest(
            null,
            fakeRequest("https://wallpaper.local/builtin/aurora_pulse/index.html")
        )
        assertNotNull(builtinResp)
        assertEquals(200, builtinResp!!.statusCode)
        val builtinBody = builtinResp.data.bufferedReader().use { it.readText() }
        assertTrue(builtinBody.contains("__liveHtmlBridgeReady"))

        // Uploaded media + HTTP Range request
        val payload = "0123456789ABCDEFGHIJ".toByteArray()
        val videoFile = createTempFile("clip.mp4", payload)
        val videoItem = repository.importVideo(Uri.fromFile(videoFile)).getOrThrow()
        val rangeResp = client.shouldInterceptRequest(
            null,
            fakeRequest(
                "https://wallpaper.local/user/${videoItem.id}/${videoItem.entryFile}",
                headers = mapOf("Range" to "bytes=4-9")
            )
        )
        assertNotNull(rangeResp)
        assertEquals(206, rangeResp!!.statusCode)
        assertEquals("bytes 4-9/20", rangeResp.responseHeaders["Content-Range"])
        val sliced = rangeResp.data.use { it.readBytes().toString(Charsets.UTF_8) }
        assertEquals("456789", sliced)

        // Path traversal must be blocked with 404
        val traversalResp = client.shouldInterceptRequest(
            null,
            fakeRequest("https://wallpaper.local/user/${videoItem.id}/../../AndroidManifest.xml")
        )
        assertNotNull(traversalResp)
        assertEquals(404, traversalResp!!.statusCode)
    }

    @Test
    fun webClient_handlesRenderProcessGoneGracefully() {
        var crashNotified = false
        val client = LocalWallpaperWebClient(
            context = context,
            repository = repository,
            onRendererCrash = { _, _ -> crashNotified = true }
        )
        val handled = client.onRenderProcessGone(null, null)
        assertTrue(handled)
        assertTrue(crashNotified)
    }

    @Test
    fun audioBridgeAndGifRenderer_safeEdgeCases() {
        val injected = AudioBridgeScript.injectIntoHtml("<html><head><title>A</title></head><body></body></html>")
        assertTrue(injected.contains("__liveHtmlApplyState"))
        val js = AudioBridgeScript.buildApplyStateJs(soundEnabled = true, volume = 5.0f, visible = true)
        assertTrue(js.contains("true,1.0,true"))

        val renderer = GifWallpaperRenderer()
        assertFalse(renderer.load(File(context.cacheDir, "does_not_exist.gif")))
        renderer.release()
    }

    // -------------------------------------------------------------------------
    // 4. Compose UI CUJ Test (HomeScreen — 3 Upload Buttons, Preview, Apply Sheet)
    // -------------------------------------------------------------------------

    @Test
    fun homeScreen_cuj_buttonsPreviewSwitcherAndApplySheetWork() {
        var state by mutableStateOf(
            MainUiState(
                activeWallpaper = repository.getActiveWallpaper(),
                settings = repository.readSettings(),
                previewStyle = PhonePreviewStyle.LOCK_SCREEN
            )
        )
        var selectedTarget: WallpaperTarget? = null

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            LiveHtmlTheme(darkTheme = state.settings.darkTheme) {
                HomeScreen(
                    uiState = state,
                    repository = repository,
                    onUploadHtml = {},
                    onUploadVideo = {},
                    onUploadGif = {},
                    onOpenApplySheet = { state = state.copy(showApplySheet = true) },
                    onDismissApplySheet = { state = state.copy(showApplySheet = false) },
                    onApplyForTarget = { target ->
                        selectedTarget = target
                        state = state.copy(
                            settings = state.settings.copy(wallpaperTarget = target),
                            showApplySheet = false
                        )
                    },
                    onPreviewStyleChange = { style -> state = state.copy(previewStyle = style) },
                    onToggleSound = { enabled ->
                        state = state.copy(settings = state.settings.copy(soundEnabled = enabled))
                    },
                    onToggleTheme = {
                        state = state.copy(settings = state.settings.copy(darkTheme = !state.settings.darkTheme))
                    },
                    onOpenSettings = { state = state.copy(showSettingsSheet = true) },
                    onDismissSettings = { state = state.copy(showSettingsSheet = false) },
                    onVolumeChange = {},
                    onMuteOtherMediaChange = {},
                    onTouchEnabledChange = {},
                    onRenderModeChange = { mode ->
                        state = state.copy(settings = state.settings.copy(renderMode = mode))
                    }
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(200)

        // Verify the 3 upload buttons & bottom Set Home / Lock Screen button exist
        composeTestRule.onNodeWithTag("upload_html_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("upload_video_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("upload_gif_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("set_home_lock_button").assertIsDisplayed()

        // Switch preview from Lock Screen to Home Screen
        composeTestRule.onNodeWithTag("pill_preview_home").performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        assertEquals(PhonePreviewStyle.HOME_SCREEN, state.previewStyle)

        // Toggle theme
        assertFalse(state.settings.darkTheme)
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        assertTrue(state.settings.darkTheme)

        // Open Apply Sheet & pick Lock Screen Only
        composeTestRule.onNodeWithTag("set_home_lock_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)

        composeTestRule.onNodeWithTag("apply_option_home_only").assertExists()
        composeTestRule.onNodeWithTag("apply_option_lock_only").assertExists()
        composeTestRule.onNodeWithTag("apply_option_both").assertExists()

        composeTestRule.onNodeWithTag("apply_option_lock_only").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        assertEquals(WallpaperTarget.LOCK_ONLY, selectedTarget)
        assertFalse(state.showApplySheet)

        // Open Settings Sheet & test sound toggle + compatibility render mode
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)

        composeTestRule.onNodeWithTag("sheet_sound_switch").assertExists().performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        assertFalse(state.settings.soundEnabled)

        composeTestRule.onNodeWithTag("sheet_render_hw").assertExists().performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        assertEquals(RenderEngineMode.AUTO_HARDWARE, state.settings.renderMode)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createTempFile(name: String, bytes: ByteArray): File {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        tempFiles.add(file)
        return file
    }

    private fun createZipFile(name: String, entries: Map<String, ByteArray>): File {
        val file = File(context.cacheDir, name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((entryName, content) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content)
                zos.closeEntry()
            }
        }
        tempFiles.add(file)
        return file
    }

    private fun minimalValidGifBytes(): ByteArray {
        // Minimal valid 1x1 GIF89a header + frame + trailer
        return byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
        )
    }

    private fun fakeRequest(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): WebResourceRequest {
        val uri = Uri.parse(url)
        return object : WebResourceRequest {
            override fun getUrl(): Uri = uri
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = headers.toMutableMap()
        }
    }
}

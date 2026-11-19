package com.sushant.lekhak

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.lekhak.ai.Suggestion
import com.sushant.lekhak.core.LekhakServiceManager
import com.sushant.lekhak.core.Palette
import com.sushant.lekhak.overlay.BubbleView
import com.sushant.lekhak.overlay.SheetView
import com.sushant.lekhak.service.LekhakAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = instrumentation.targetContext
    private val pkg = ctx.packageName

    private fun shell(cmd: String) {
        instrumentation.uiAutomation.executeShellCommand(cmd).close()
        Thread.sleep(400)
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900)
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AppShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("Walkthrough", "Saved screenshot $name")
    }

    private fun waitTag(tag: String, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun gone(tag: String, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

    private fun prefs() = ctx.getSharedPreferences(LekhakServiceManager.PREFS, Context.MODE_PRIVATE)

    private fun setTheme(mode: String) {
        prefs().edit().putString("theme", mode).commit()
        LekhakServiceManager.themeMode = mode
    }

    /** Full first-launch onboarding: overlay granted → accessibility dialog → key highlight → ready. */
    @Test fun a_firstLaunchOnboarding() {
        prefs().edit().clear().putBoolean("is_first_launch", true).putString("theme", "dark").commit()
        ctx.getSharedPreferences(LekhakServiceManager.SECRETS, Context.MODE_PRIVATE).edit().clear().commit()
        LekhakServiceManager.themeMode = "dark"
        LekhakServiceManager.saveGemini("", "")
        LekhakServiceManager.setProvider(true)
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        shell("settings put secure enabled_accessibility_services null")

        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        waitTag("dialog_open_settings")
        shot("01_onboarding_accessibility_dialog_dark")

        // Turn on the accessibility service the way the user would in system settings.
        shell("settings put secure enabled_accessibility_services $pkg/${LekhakAccessibilityService::class.java.name}")
        shell("settings put secure accessibility_enabled 1")
        assertTrue(LekhakAccessibilityService.isEnabled(ctx))
        gone("dialog_open_settings")
        if (!LekhakServiceManager.hasBuiltInGeminiKey()) {
            waitTag("key_tooltip")
            shot("02_onboarding_key_highlight_dark")
            compose.onNodeWithTag("key_field").performTextInput("AIza-test-key-123")
            compose.onNodeWithTag("toggle_key").performClick()
            shot("03_onboarding_key_typed_dark")
            compose.onNodeWithTag("save_key").performClick()
        }
        compose.waitUntil(15_000) { !prefs().getBoolean("is_first_launch", true) }
        assertFalse(prefs().getBoolean("is_first_launch", true))
        assertTrue(LekhakServiceManager.isAiReady())
        scenario.close()
    }

    @Test fun b_settingsDark() = settingsWalk("dark")

    @Test fun c_settingsLight() = settingsWalk("light")

    private fun settingsWalk(mode: String) {
        prefs().edit().putBoolean("is_first_launch", false).commit()
        setTheme(mode)
        LekhakServiceManager.setProvider(true)
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        waitTag("perm_accessibility")
        shot("1${if (mode == "dark") "0" else "5"}_settings_top_$mode")
        compose.onNodeWithTag("key_field").performScrollTo()
        shot("1${if (mode == "dark") "1" else "6"}_settings_gemini_$mode")
        compose.onNodeWithTag("provider_openai").performClick()
        waitTag("url_field")
        compose.onNodeWithTag("url_field").performScrollTo()
        shot("1${if (mode == "dark") "2" else "7"}_settings_openai_$mode")
        compose.onNodeWithTag("about_email").performScrollTo()
        shot("1${if (mode == "dark") "3" else "8"}_settings_about_$mode")
        compose.onNodeWithTag("provider_gemini").performScrollTo().performClick()
        assertTrue(LekhakServiceManager.useGemini)
        // Revoked-permission banner
        shell("settings put secure enabled_accessibility_services null")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("revoked_banner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("revoked_banner").performScrollTo()
        shot("1${if (mode == "dark") "4" else "9"}_settings_revoked_$mode")
        shell("settings put secure enabled_accessibility_services $pkg/${LekhakAccessibilityService::class.java.name}")
        scenario.close()
    }

    @Test fun d_themeSwitchApplies() {
        prefs().edit().putBoolean("is_first_launch", false).commit()
        setTheme("dark")
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        waitTag("theme_light")
        compose.onNodeWithTag("theme_light").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("light", prefs().getString("theme", ""))
        compose.onNodeWithTag("theme_dark").performClick()
        compose.waitForIdle()
        assertEquals("dark", prefs().getString("theme", ""))
        scenario.close()
    }

    /** Renders the WhatsApp overlay sheet inside an activity window so it can be captured. */
    @Test fun e_sheetStates() {
        prefs().edit().putBoolean("is_first_launch", false).commit()
        for (mode in listOf("dark", "light")) {
            setTheme(mode)
            val scenario = ActivityScenario.launch(SettingsActivity::class.java)
            waitTag("perm_accessibility")
            var sheet: SheetView? = null
            scenario.onActivity { a ->
                val palette = if (mode == "dark") Palette.Dark else Palette.Light
                val root = a.window.decorView as ViewGroup
                val s = SheetView(a, palette, root.height)
                root.addView(s, FrameLayout.LayoutParams(-1, -1))
                s.setMode(false)
                s.showLoading()
                sheet = s
            }
            shot("2${if (mode == "dark") "0" else "4"}_sheet_loading_$mode")
            scenario.onActivity {
                sheet!!.showSuggestions(listOf(
                    Suggestion("Casual", "Haan bhai, kal 5 baje pakka milte hain!"),
                    Suggestion("Polished", "Yes, I am free tomorrow. Shall we meet at 5 PM?"),
                    Suggestion("Witty", "Tumhare liye toh calendar bhi khali kar diya hai 😄"),
                ))
            }
            shot("2${if (mode == "dark") "1" else "5"}_sheet_replies_$mode")
            scenario.onActivity {
                sheet!!.setMode(true)
                sheet!!.showSuggestions(listOf(
                    Suggestion("Polished (Professional)", "I will share the file with you by this evening."),
                    Suggestion("Concise", "Sending the file tonight."),
                    Suggestion("Friendly", "Shaam tak file bhej deta hoon, don't worry!"),
                ))
            }
            shot("2${if (mode == "dark") "2" else "6"}_sheet_rewrite_$mode")
            scenario.onActivity {
                sheet!!.showSuggestions(listOf(Suggestion("Error", "Could not connect. Check your internet.", isError = true)))
                val root = it.window.decorView as ViewGroup
                val b = BubbleView(it)
                root.addView(b, FrameLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height).apply {
                    leftMargin = root.width - b.layoutParams.width - 40
                    topMargin = (root.height * 0.18f).toInt()
                })
            }
            shot("2${if (mode == "dark") "3" else "7"}_sheet_error_and_bubble_$mode")
            scenario.close()
        }
    }
}

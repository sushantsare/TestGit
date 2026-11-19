package com.sushant.gymmanager

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.sushant.gymmanager.data.local.ThemeMode
import com.sushant.gymmanager.ui.lock.AppLockScreen
import com.sushant.gymmanager.ui.theme.GymManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class AppLockScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rendersAndUnlocks() {
        var clicks = 0
        compose.setContent {
            GymManagerTheme(ThemeMode.LIGHT) { AppLockScreen(gymName = "PowerFit Gym", onUnlock = { clicks++ }) }
        }
        compose.onNodeWithTag("app_lock").assertIsDisplayed()
        compose.onNodeWithText("PowerFit Gym").assertIsDisplayed()
        compose.onNodeWithText("Biometric Protection Enabled").assertIsDisplayed()
        compose.onNodeWithText("Unlock Application").assertIsDisplayed().performClick()
        assertEquals(1, clicks)

        // Render snapshot (saved for visual review)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/outputs/roborazzi").apply { mkdirs() }
        File(out, "robolectric_app_lock.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

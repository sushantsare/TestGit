package com.sushant.bahikhata

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sushant.bahikhata.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clicks through EVERY screen like a user (standing rule: every page is tested), saving light and dark screenshots
 * to Pictures/AppShots for the visual review. Real interactions: setup, adding an entry with the keypad and
 * the 3 steps, tabs, dialogs. Deep screens are opened the way notifications open them (route intent).
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val perms: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    private val app get() = ApplicationProvider.getApplicationContext<App>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var shots = 0

    private fun log(m: String) = Log.i("Walk", m)

    private fun waitTag(tag: String, ms: Long = 8_000) = compose.waitUntil(ms) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(t: String, ms: Long = 8_000) = compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun click(tag: String) { log("click $tag"); waitTag(tag); compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().performClick(); compose.waitForIdle() }
    private fun type(tag: String, text: String) { waitTag(tag); compose.onNodeWithTag(tag).performTextClearance(); compose.onNodeWithTag(tag).performTextInput(text); hideKeyboard() }

    private fun hideKeyboard() {
        scenario.onActivity { a ->
            val v = a.currentFocus ?: a.window.decorView
            (a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
        }
        compose.waitForIdle()
    }

    private fun go(route: String, waitFor: String? = null) {
        scenario.onActivity { a -> a.startActivity(Intent(a, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        compose.waitForIdle()
        waitFor?.let { waitTag(it) }
        log("opened $route")
    }

    private fun back() { InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); Thread.sleep(500); compose.waitForIdle() }

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(900)
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val file = "%02d_%s.png".format(++shots, name)
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppShots")
        }
        val cr = app.contentResolver
        cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { uri -> cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        log("shot $file")
    }

    @Test fun walkthroughEveryScreen() {
        try { walk() } catch (t: Throwable) {
            runCatching { shot("FAILED_here") }
            runCatching { compose.onRoot(useUnmergedTree = true).printToLog("WalkTree") }
            throw t
        }
    }

    private fun walk() {
        runBlocking { app.repo.wipeAll() }
        app.prefs.update { Settings(secureScreens = false) }
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // ---- first launch setup
        waitTag("setup_next"); shot("setup_welcome")
        click("setup_next"); waitTag("setup_add_BANK"); shot("setup_accounts")
        click("setup_add_BANK"); waitTag("f_name")
        type("f_name", "SBI Savings"); type("f_opening", "18450"); type("f_last4", "4417")
        click("upi_GPay"); shot("account_add_bank")
        click("save_account"); waitTag("setup_acc_SBI Savings"); shot("setup_accounts_added")
        hideKeyboard(); click("setup_next"); waitText("Any udhar"); waitTag("btn_setup_person"); shot("setup_udhar")
        click("setup_next"); waitTag("sw_setup_lock"); shot("setup_lock_sms")
        click("setup_next"); waitTag("tab_home")

        // ---- realistic data
        runBlocking { Seed.fill(app.repo) }
        app.prefs.update { it.copy(setupDone = true) }
        waitTag("net"); waitText("Swiggy"); shot("home")

        // ---- add an expense: amount -> category -> account, with the keypad
        click("fab_add"); waitTag("key_4")
        click("key_1"); click("key_2"); click("key_0"); click("key_+"); click("key_8"); click("key_0")
        type("f_merchant", "Chai point"); shot("add_amount_keypad")
        click("next"); waitTag("cat_Food"); shot("add_category")
        click("cat_Food"); waitTag("save"); click("acc_Cash"); shot("add_details")
        click("row_split"); waitTag("split_Rahul Kumar"); shot("add_split_dialog"); click("split_Rahul Kumar"); click("split_ok")
        click("row_repeat"); waitTag("pick_Never"); shot("add_repeat_sheet"); click("pick_Never")
        click("save"); waitTag("fab_add"); waitText("Chai point"); shot("home_after_add")

        // ---- income + transfer chips
        go("add?type=INCOME", "key_5"); click("key_5"); click("key_0"); click("key_0"); shot("add_income"); back(); compose.waitForIdle()

        // ---- entries list, calendar, search, detail
        click("tab_entries"); waitTag("entries_list"); shot("entries")
        click("act_calendar"); waitTag("calendar"); shot("entries_calendar"); click("act_calendar")
        go("search", "f_search"); type("f_search", "swig"); waitText("RESULTS"); shot("search")
        val entries = runBlocking { app.repo.dao.entriesNow() }
        val swiggy = entries.first { it.merchant == "Swiggy" }
        go("entry/${swiggy.id}", "entry_detail"); shot("entry_detail_split")
        click("act_edit"); waitTag("save"); shot("entry_edit"); back()

        // ---- accounts
        val accs = runBlocking { app.repo.dao.accountsNow() }
        val sbi = accs.first { it.name == "SBI Savings" }; val hdfc = accs.first { it.name == "HDFC Millennia" }
        click("tab_accounts"); waitTag("accounts_list"); shot("accounts")
        go("account/${sbi.id}", "account_detail"); shot("account_bank")
        click("btn_adjust"); waitTag("dlg_field"); shot("adjust_balance_dialog"); back()
        go("account/${hdfc.id}", "pay_bill"); shot("card_detail")
        click("pay_bill"); waitTag("do_transfer"); shot("pay_card_bill")
        click("do_transfer"); waitTag("pay_bill"); shot("card_after_payment")
        go("transfer/withdraw?acc=${sbi.id}", "do_transfer"); type("f_amount", "1000"); shot("withdraw_cash"); back()
        go("account_edit/${hdfc.id}?type=CARD", "save_account"); shot("account_edit_card"); back()
        go("account_edit/0?type=WALLET", "save_account"); shot("account_add_wallet"); back()

        // ---- udhar
        click("tab_udhar"); waitTag("udhar_list"); shot("udhar")
        val rahul = runBlocking { app.repo.dao.peopleNow().first { it.name == "Rahul Kumar" } }
        go("person/${rahul.id}", "person_detail"); shot("person")
        click("you_gave"); waitTag("f_uamount"); type("f_uamount", "250"); shot("you_gave_dialog"); click("udhar_save"); waitText("₹250")
        click("btn_settle"); waitTag("confirm"); shot("settle_confirm"); back()

        // ---- reports, budgets, goals
        click("tab_reports"); waitTag("reports"); shot("reports")
        go("budgets", "budgets"); shot("budgets_goals")

        // ---- SMS
        go("sms", "sms_list"); shot("sms_suggestions")
        click("tab_look"); waitText("Fix & teach"); shot("sms_needs_look")
        click("teach_" + runBlocking { app.repo.dao.smsNow().first { it.key == "s4" }.id }); waitTag("teach_ok"); shot("sms_teach_dialog"); back()
        click("tab_missed"); waitText("Bring back"); shot("sms_missed")
        click("tab_sugg"); click("ok_" + runBlocking { app.repo.dao.smsNow().first { it.key == "s1" }.id }); waitText("Saved"); shot("sms_after_ok")
        go("sms_setup", "allow_sms"); shot("sms_permission")

        // ---- settings and sub pages
        go("settings", "settings_list"); shot("settings")
        go("categories"); waitTag("catrow_Food"); shot("categories")
        click("catrow_Food"); waitTag("cat_ok"); shot("category_edit"); back()
        go("recurring"); waitText("Hostel rent"); shot("recurring")
        go("backup"); waitTag("sw_drive"); shot("backup")
        go("export"); waitTag("fmt_pdf"); shot("export")
        go("diagnostics"); waitTag("btn_export_log"); shot("diagnostics")
        go("about"); waitTag("email"); shot("about")

        // ---- dark theme: main screens again
        app.prefs.update { it.copy(theme = ThemeMode.DARK) }
        go("home", "net"); shot("dark_home")
        go("add?type=EXPENSE", "key_1"); click("key_7"); click("key_5"); shot("dark_add"); back()
        go("entries", "entries_list"); shot("dark_entries")
        go("accounts", "accounts_list"); shot("dark_accounts")
        go("account/${hdfc.id}", "pay_bill"); shot("dark_card")
        go("udhar", "udhar_list"); shot("dark_udhar")
        go("person/${rahul.id}", "person_detail"); shot("dark_person")
        go("reports", "reports"); shot("dark_reports")
        go("sms", "sms_list"); shot("dark_sms")
        go("settings", "settings_list"); shot("dark_settings")

        // ---- hide amounts
        app.prefs.update { it.copy(theme = ThemeMode.LIGHT, hideAmounts = true) }
        go("home", "net"); shot("home_hidden_amounts")
        app.prefs.update { it.copy(hideAmounts = false) }

        // ---- empty states
        runBlocking { app.repo.wipeAll() }
        go("home"); waitTag("empty_action"); shot("home_empty")
        go("udhar"); waitTag("empty_action"); shot("udhar_empty")
        go("entries"); waitTag("empty_action"); shot("entries_empty")
        log("walkthrough done: $shots screenshots")
        scenario.close()
    }
}

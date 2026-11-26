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

    // Soft checks: a missing element is recorded (with a screenshot) and the walk carries on, so one run shows
    // every problem; the test fails at the end if anything was recorded.
    private val problems = mutableListOf<String>()
    private fun problem(what: String) { problems += what; log("PROBLEM $what"); runCatching { shot("PROBLEM_" + what.replace(Regex("[^A-Za-z0-9_]"), "_").take(40)) } }
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 8_000): Boolean = runCatching { compose.waitUntil(ms) { has(tag) } }.isSuccess.also { if (!it && ms >= 8_000) problem("missing $tag") }
    private fun waitText(t: String, ms: Long = 8_000): Boolean = runCatching { compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }.isSuccess.also { if (!it) problem("missing text $t") }
    private fun click(tag: String) { dismissSystemDialogs(); log("click $tag"); if (!waitTag(tag)) return; runCatching { compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().also { n -> runCatching { n.performScrollTo() } }.performClick() }.onFailure { problem("click $tag: ${it.message?.take(60)}") }; compose.waitForIdle() }
    private fun type(tag: String, text: String) { if (!waitTag(tag)) return; compose.onNodeWithTag(tag).performTextClearance(); compose.onNodeWithTag(tag).performTextInput(text); hideKeyboard() }

    /** Click and wait for the next screen; one retry (logged) if the first tap was swallowed by a recomposition. */
    private fun clickTo(tag: String, expect: String) {
        click(tag)
        if (!waitTag(expect, 4_000)) { problem("first tap on $tag did not open $expect"); click(tag); waitTag(expect) }
    }

    /** Go to a top-level tab the way a user would: press back until the tab bar shows, then tap the tab. */
    private fun tab(r: String) {
        repeat(5) {
            if (has("tab_$r")) return@repeat
            if (has("confirm")) { problem("unexpected discard prompt before tab $r"); click("confirm") } else back()
        }
        click("tab_$r")
    }

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

    /** Back on a form with typing shows "Discard?" — confirm it (screenshotting it once). */
    private var discardShot = false
    private fun backDiscard() {
        back()
        if (waitTag("confirm", 3_000)) { if (!discardShot) { shot("discard_prompt"); discardShot = true }; click("confirm") }
        else problem("no discard prompt after typing")
    }

    /** The emulator's own launcher sometimes shows "isn't responding" over the app; tap Wait and carry on. */
    private fun dismissSystemDialogs() {
        runCatching {
            val d = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val wait = d.findObject(androidx.test.uiautomator.By.text("Wait"))
            if (wait != null && d.findObject(androidx.test.uiautomator.By.textContains("isn't responding")) != null) { log("dismissed system ANR dialog"); wait.click(); Thread.sleep(500) }
        }
    }

    private fun back() { dismissSystemDialogs(); InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); Thread.sleep(500); compose.waitForIdle() }

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(900); dismissSystemDialogs()
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
        log("problems=${problems.size}")
        org.junit.Assert.assertTrue("Walkthrough problems: " + problems.joinToString("; "), problems.isEmpty())
    }

    private fun walk() {
        runBlocking { app.repo.wipeAll() }
        app.prefs.update { Settings(secureScreens = false) }
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // ---- first launch: opening animation, then the 9-step setup guide
        if (has("intro")) shot("setup_intro")
        waitTag("setup_next", 12_000); waitText("Welcome to Bahi Khata"); shot("setup_1_welcome")
        click("setup_next"); waitTag("setup_add_BANK"); waitText("Step 2 of 9"); shot("setup_2_accounts")
        click("setup_add_BANK"); waitTag("f_name")
        type("f_name", "SBI Savings"); type("f_opening", "18450"); type("f_last4", "4417")
        click("upi_GPay"); shot("account_add_bank")
        click("save_account"); waitTag("setup_acc_SBI Savings"); shot("setup_2_accounts_added")
        hideKeyboard(); click("setup_next"); waitText("Any udhar"); waitTag("btn_setup_person"); shot("setup_3_udhar")
        click("setup_next"); waitText("Read bank SMS?"); shot("setup_4_sms")
        click("skip"); waitText("Stay in the loop"); shot("setup_5_notifications")
        click("setup_next"); waitTag("sw_setup_lock"); shot("setup_6_privacy")
        click("setup_next"); waitText("Back it up"); shot("setup_7_backup")
        click("skip"); waitTag("sw_setup_black"); shot("setup_8_look")
        click("setup_next"); waitText("You're all set"); shot("setup_9_done")
        click("setup_next"); waitTag("tab_home")

        // ---- realistic data
        runBlocking { Seed.fill(app.repo) }
        app.prefs.update { it.copy(setupDone = true) }
        waitTag("net"); waitText("Swiggy"); compose.waitForIdle(); Thread.sleep(1500); shot("home")

        // ---- add an expense: amount -> category -> account, with the keypad
        clickTo("fab_add", "key_4")
        click("key_1"); click("key_2"); click("key_0"); click("key_+"); click("key_8"); click("key_0")
        type("f_merchant", "Chai point"); shot("add_amount_keypad")
        click("next"); waitTag("cat_Food"); shot("add_category")
        click("cat_Food"); waitTag("save"); click("acc_Cash"); shot("add_details")
        click("row_split"); waitTag("split_Rahul Kumar"); shot("add_split_dialog"); click("split_Rahul Kumar"); click("split_ok")
        click("row_repeat"); waitTag("pick_Never"); shot("add_repeat_sheet"); click("pick_Never")
        click("save"); waitTag("fab_add"); waitText("Chai point"); shot("home_after_add")

        // ---- income + transfer chips
        go("add?type=INCOME", "key_5"); click("key_5"); click("key_0"); click("key_0"); shot("add_income"); backDiscard(); compose.waitForIdle()

        // ---- entries list, calendar, search, detail
        click("tab_entries"); waitTag("entries_list"); shot("entries")
        click("act_calendar"); waitTag("calendar"); shot("entries_calendar"); click("act_calendar")
        go("search", "f_search"); type("f_search", "swig"); waitText("RESULTS"); shot("search")
        back(); tab("home"); click("act_search"); waitTag("f_search"); shot("search_from_home_icon"); back(); waitTag("act_search")
        val entries = runBlocking { app.repo.dao.entriesNow() }
        val swiggy = entries.first { it.merchant == "Swiggy" }
        go("entry/${swiggy.id}", "entry_detail"); shot("entry_detail_split")
        click("act_edit"); waitTag("save"); shot("entry_edit"); back()
        val kirana = entries.filter { it.merchant == "Ravi Kirana" }.maxBy { it.time }
        go("entry/${kirana.id}", "items_card"); shot("entry_items")
        runCatching { compose.onAllNodesWithText("Edit", useUnmergedTree = true).onFirst().performClick() }; compose.waitForIdle()
        if (waitTag("items_save", 4_000)) { shot("entry_items_dialog"); back() }
        go("entry/${entries.first { it.cancelled }.id}", "cancelled_note"); shot("entry_cancelled")
        go("entry/${entries.first { it.refundOf != null }.id}", "entry_detail"); waitText("Refund for"); shot("entry_refund")

        // ---- accounts
        val accs = runBlocking { app.repo.dao.accountsNow() }
        val sbi = accs.first { it.name == "SBI Savings" }; val hdfc = accs.first { it.name == "HDFC Millennia" }
        tab("accounts"); waitTag("accounts_list"); shot("accounts")
        go("account/${sbi.id}", "account_detail"); waitTag("balance_check"); shot("account_bank_balance_check")
        click("btn_adjust"); waitTag("dlg_field"); shot("adjust_balance_dialog"); back()
        go("account/${hdfc.id}", "pay_bill"); waitTag("limit_used"); shot("card_detail")
        click("bill_row"); if (waitTag("dlg_field", 3_000)) { shot("card_min_due_dialog"); back() }
        click("pay_bill"); waitTag("do_transfer"); shot("pay_card_bill")
        click("do_transfer"); waitTag("pay_bill"); shot("card_after_payment")
        go("transfer/withdraw?acc=${sbi.id}", "do_transfer"); type("f_amount", "1000"); shot("withdraw_cash"); backDiscard()
        go("account_edit/${hdfc.id}?type=CARD", "save_account"); shot("account_edit_card"); back()
        go("account_edit/0?type=WALLET", "save_account"); shot("account_add_wallet"); back()
        // delete with undo
        val paytm = runBlocking { app.repo.dao.accountsNow().first { it.name == "Paytm Wallet" } }
        go("account/${paytm.id}", "account_detail"); click("act_delete_account"); click("confirm"); waitText("deleted"); shot("account_deleted_undo")
        runCatching { compose.onAllNodesWithText("Undo", useUnmergedTree = true).onFirst().performClick() }; compose.waitForIdle(); Thread.sleep(1500)
        if (runBlocking { app.repo.dao.accountsNow().none { it.name == "Paytm Wallet" } }) problem("account undo did not bring Paytm Wallet back")

        // ---- udhar
        tab("udhar"); waitTag("udhar_list"); shot("udhar")
        val rahul = runBlocking { app.repo.dao.peopleNow().first { it.name == "Rahul Kumar" } }
        go("person/${rahul.id}", "person_detail"); shot("person")
        click("you_gave"); waitTag("f_uamount"); type("f_uamount", "250"); shot("you_gave_dialog"); click("udhar_save"); waitText("₹250")
        click("btn_settle"); waitTag("confirm"); shot("settle_confirm"); back()

        // ---- reports, budgets, goals
        tab("reports"); waitTag("open_insights"); shot("reports")
        click("open_insights"); waitTag("insights_list"); shot("insights")
        listOf("subs" to "sub_Netflix", "payday" to "insight_total", "charges" to "insight_total", "invest" to "insight_total", "people" to "person_sum_Rahul Kumar",
            "prices" to "price_Milk", "year" to "insight_total").forEach { (pg, want) -> go("insights/$pg", "insight_$pg"); waitTag(want); shot("insight_$pg") }
        go("reminders", "reminders_list"); waitTag("rem_" + runBlocking { app.repo.dao.remindersNow().first { it.key == "r1" }.id }); shot("reminders")
        tab("reports"); waitTag("reports")
        click("period_CUSTOM"); if (waitTag("range_ok", 4_000)) { shot("reports_custom_range"); click("range_ok") }; waitTag("reports"); shot("reports_custom"); click("period_MONTH")
        go("budgets", "budgets"); shot("budgets_goals")

        // ---- SMS
        app.prefs.batteryTipDue = true; app.prefs.batteryTipShown = false
        go("sms", "sms_list"); shot("sms_suggestions")
        val s6 = runBlocking { app.repo.dao.smsNow().first { it.key == "s6" } }
        val s7 = runBlocking { app.repo.dao.smsNow().first { it.key == "s7" } }
        waitTag("sms_${s6.id}"); compose.onNodeWithTag("sms_list").performScrollToNode(hasTestTag("sms_${s6.id}")); waitTag("name_upi"); shot("sms_name_upi_and_items")
        click("sms_items_${s6.id}"); waitTag("items_save"); type("item_name_0", "Tea"); type("item_price_0", "40"); click("item_add"); type("item_name_1", "Biscuit"); type("item_price_1", "100")
        waitTag("items_left"); shot("sms_items_dialog"); click("items_save")
        click("name_upi_open"); waitTag("f_upi_name"); type("f_upi_name", "Sharma Tea Stall"); shot("sms_name_upi_form"); click("name_upi_save")
        compose.waitUntil(5_000) { runBlocking { app.repo.dao.upiName("vpa:paytmqr28100505@paytm") != null } }
        compose.onNodeWithTag("sms_list").performScrollToNode(hasTestTag("sms_${s7.id}")); waitTag("settle_${s7.id}"); shot("sms_settle_udhar")
        if (waitTag("battery_fix", 3_000)) { click("battery_fix"); waitTag("battery_open"); shot("sms_battery_guide"); back() }
        click("tab_look"); waitText("Fix & teach"); shot("sms_needs_look")
        click("teach_" + runBlocking { app.repo.dao.smsNow().first { it.key == "s4" }.id }); waitTag("teach_ok"); shot("sms_teach_dialog"); back()
        click("tab_missed"); waitText("Bring back"); shot("sms_missed")
        click("tab_sugg"); click("ok_" + runBlocking { app.repo.dao.smsNow().first { it.key == "s1" }.id }); waitText("Saved"); shot("sms_after_ok")
        go("sms_setup", "allow_sms"); shot("sms_permission")

        // ---- settings and sub pages
        go("settings", "settings_list"); shot("settings")
        click("nav_Budget month starts on"); compose.waitForIdle(); Thread.sleep(600); shot("settings_budget_start"); back()
        go("categories"); waitTag("catrow_Food"); shot("categories")
        click("catrow_Food"); waitTag("cat_ok"); shot("category_edit"); back()
        go("recurring"); waitText("Hostel rent"); shot("recurring")
        go("backup"); waitTag("sw_drive"); shot("backup")
        go("export"); waitTag("fmt_pdf"); shot("export")
        go("diagnostics"); waitTag("btn_export_log"); shot("diagnostics")
        go("about"); waitTag("email"); shot("about")
        go("hide_amounts", "hide_list"); waitTag("sw_area_UDHAR"); shot("hide_amounts")
        go("upi_names", "upi_list"); waitTag("upi_Ravi Kirana"); shot("upi_names")
        click("upi_Ravi Kirana"); waitTag("upi_edit_ok"); shot("upi_name_edit"); back()

        // ---- dark theme: main screens again
        app.prefs.update { it.copy(theme = ThemeMode.DARK) }
        go("home", "net"); shot("dark_home")
        go("add?type=EXPENSE", "key_1"); click("key_7"); click("key_5"); shot("dark_add"); backDiscard()
        go("entries", "entries_list"); shot("dark_entries")
        go("accounts", "accounts_list"); shot("dark_accounts")
        go("account/${hdfc.id}", "pay_bill"); shot("dark_card")
        go("udhar", "udhar_list"); shot("dark_udhar")
        go("person/${rahul.id}", "person_detail"); shot("dark_person")
        go("reports", "reports"); shot("dark_reports")
        go("sms", "sms_list"); shot("dark_sms")
        go("settings", "settings_list"); shot("dark_settings")
        go("insights", "insights_list"); shot("dark_insights")
        // ---- pure black (AMOLED)
        app.prefs.update { it.copy(pureBlack = true) }
        go("home", "net"); shot("black_home")
        go("entries", "entries_list"); shot("black_entries")
        go("accounts", "accounts_list"); shot("black_accounts")
        go("settings", "settings_list"); waitTag("sw_pure_black"); shot("black_settings")
        app.prefs.update { it.copy(pureBlack = false) }

        // ---- hide amounts
        app.prefs.update { it.copy(theme = ThemeMode.LIGHT, hideAmounts = true) }
        go("home", "net"); shot("home_hidden_amounts")
        // per-area: Udhar switched off stays visible while Home stays hidden
        app.prefs.update { it.copy(hideAreas = "HOME,ENTRIES,ACCOUNTS,REPORTS,NOTIF") }
        go("udhar", "udhar_list"); shot("udhar_not_hidden_area_off")
        go("hide_amounts", "hide_list"); shot("hide_amounts_udhar_off")
        app.prefs.update { it.copy(hideAmounts = false, hideAreas = HideArea.ALL) }

        // ---- empty states
        runBlocking { app.repo.wipeAll() }
        go("home"); waitTag("empty_action"); shot("home_empty")
        go("udhar"); waitTag("empty_action"); shot("udhar_empty")
        go("entries"); waitTag("empty_action"); shot("entries_empty")
        log("walkthrough done: $shots screenshots")
        scenario.close()
    }
}

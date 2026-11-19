package com.sushant.gymmanager

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.data.repository.PlanInput
import com.sushant.gymmanager.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Seeds realistic data, then walks through every screen like a real user would,
 * verifying the core flows and saving a screenshot of each step.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ctx = instrumentation.targetContext
    private val app get() = ctx.applicationContext as GymApp

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(900)
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        saveBitmap(name, bmp)
    }

    private fun saveBitmap(name: String, bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GymShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("Walkthrough", "Saved screenshot $name")
    }

    private fun waitTag(tag: String, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitText(text: String, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun goTo(route: String) {
        compose.onNodeWithTag("nav_menu").performClick()
        waitTag("drawer_$route")
        compose.onNodeWithTag("drawer_$route").performScrollTo().performClick()
        compose.waitForIdle()
        Thread.sleep(500)
    }

    private fun tagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    @Before
    fun seed(): Unit = runBlocking {
        val repo = app.repository
        repo.resetAll()
        repo.saveSettings(GymSettingsEntity(gymName = "PowerFit Gym", themeMode = "LIGHT"))
        val today = LocalDate.now()
        suspend fun add(
            roll: String, name: String, phone: String, from: String, to: String,
            joinedDaysAgo: Long, plan: String, fee: Double, autoRenew: Boolean = true,
            father: String? = null, dob: String = ""
        ): Long {
            val shift = DateUtils.determineShift(from, "05:30", "10:30")
            return repo.addMember(
                MemberEntity(
                    rollNumber = roll, fullName = name, whatsappNumber = phone,
                    dateOfJoining = today.minusDays(joinedDaysAgo).toString(), dateOfBirth = dob,
                    fatherName = father, shift = shift, fromTime = from, toTime = to
                ),
                PlanInput(plan, 0, 0, fee, autoRenew)
            )
        }
        val aarav = add("1", "Aarav Sharma", "9876543210", "06:00", "07:00", 20, PlanType.ONE_MONTH, 1000.0, father = "Rajesh Sharma", dob = "2001-04-12")
        val priya = add("2", "Priya Singh", "9812345678", "06:00", "07:00", 27, PlanType.ONE_MONTH, 1000.0)
        val rohit = add("3", "Rohit Kumar", "9898989898", "07:00", "08:00", 65, PlanType.ONE_MONTH, 1000.0)
        val neha = add("4", "Neha Verma", "9123456789", "07:00", "08:30", 87, PlanType.THREE_MONTHS, 2700.0)
        val vikram = add("5", "Vikram Yadav", "9001122334", "18:00", "19:00", 10, PlanType.SIX_MONTHS, 5000.0)
        add("6", "Anjali Gupta", "9334455667", "18:00", "19:00", 40, PlanType.ONE_MONTH, 1000.0, autoRenew = false)
        val sunny = add("7", "Sunny Raj", "9445566778", "19:30", "21:00", 5, PlanType.TWELVE_MONTHS, 9000.0)
        val manish = add("8", "Manish Patel", "9556677889", "17:00", "18:00", 100, PlanType.ONE_MONTH, 1000.0)
        repo.setActive(manish, false)

        val created = repo.checkAndGenerateAutoRenewalCycles()
        Log.i("Walkthrough", "Auto-renewal created $created cycles")
        val unpaid = repo.unpaidCycles.first()
        // Rohit joined 65 days ago on a 1-month auto-renew plan -> 3 cycles by now.
        assertEquals(3, unpaid.count { it.memberId == rohit })
        repo.markPaid(unpaid.filter { it.memberId == vikram || it.memberId == sunny }.map { it.id }, "UPI")
        // A partial payment for Rohit's oldest cycle
        repo.recordPayments(mapOf(unpaid.filter { it.memberId == rohit }.minByOrNull { it.cycleStart }!!.id to 400.0), "CASH")
        repo.saveExpense(com.sushant.gymmanager.data.local.ExpenseEntity(date = today.toString(), category = "Rent", amount = 6000.0, note = "Hall rent"))
        repo.saveExpense(com.sushant.gymmanager.data.local.ExpenseEntity(date = today.minusMonths(1).toString(), category = "Electricity", amount = 1800.0))

        val todayIso = today.toString()
        repo.saveAttendance(todayIso, Shift.MORNING, mapOf(aarav to true, priya to true, rohit to false, neha to true), emptyMap())
        repo.saveAttendance(today.minusDays(1).toString(), Shift.MORNING, mapOf(aarav to true, priya to false, rohit to true, neha to true), emptyMap())
    }

    @Test
    fun fullWalkthrough() {
        ActivityScenario.launch(MainActivity::class.java)
        waitTag("dashboard")
        waitText("Welcome back, Coach")
        shot("01_dashboard")

        compose.onNodeWithTag("dashboard").performTouchInput { swipeUp() }
        shot("02_dashboard_scrolled")

        // Drawer + master list
        compose.onNodeWithTag("nav_menu").performClick()
        waitTag("drawer_members")
        shot("03_drawer")
        compose.onNodeWithTag("drawer_members").performClick()
        waitTag("member_list")
        shot("04_master_list")

        // Member card
        compose.onNodeWithText("Aarav Sharma").performClick()
        waitTag("member_card")
        shot("05_member_card")
        compose.onNodeWithContentDescription("Close").performClick()

        // Add a member
        compose.onNodeWithTag("fab_add").performClick()
        waitTag("field_roll")
        shot("06_add_member_empty")
        compose.onNodeWithTag("field_roll").performTextInput("9")
        compose.onNodeWithTag("field_name").performTextInput("Karan Mehta")
        compose.onNodeWithTag("field_whatsapp").performTextInput("9123456780")
        compose.onNodeWithTag("field_from").performScrollTo().performClick()
        waitText("Workout starts at")
        shot("07_time_picker")
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        shot("08_add_member_filled")
        compose.onNodeWithTag("save_member").performClick()
        waitTag("member_list")
        compose.waitUntil(15_000) { runBlocking { app.repository.activeMembers.first().any { it.rollNumber == "9" } } }
        compose.onNodeWithTag("member_list").performScrollToNode(hasTestTag("member_9"))
        shot("09_master_list_after_add")

        // Duplicate roll number is rejected
        compose.onNodeWithTag("fab_add").performClick()
        waitTag("field_roll")
        compose.onNodeWithTag("field_roll").performTextInput("9")
        waitText("Roll number already in use")
        shot("10_duplicate_roll")
        compose.onNodeWithTag("nav_back").performClick()
        compose.waitForIdle()
        if (compose.onAllNodes(hasText("Discard")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Discard").performClick()
        }
        waitTag("member_list")

        // Search
        compose.onNodeWithTag("search").performTextInput("Karan")
        compose.waitForIdle()
        shot("11_search")
        compose.onNodeWithTag("search").performTextClearance()

        // Attendance
        goTo("attendance")
        waitTag("present_summary")
        shot("12_attendance")
        compose.onNodeWithTag("all_present").performClick()
        compose.onNodeWithTag("save_attendance").performClick()
        compose.waitForIdle()
        shot("13_attendance_saved")
        compose.onNodeWithTag("tab_evening").performClick()
        compose.waitForIdle()
        shot("14_attendance_evening")

        // Payments
        goTo("payments")
        waitTag("payments_list")
        shot("15_payments")
        compose.onAllNodes(tagPrefix("cycle_"))[0].performClick()
        waitTag("mark_paid")
        shot("16_payments_selected")
        compose.onNodeWithTag("mark_paid").performClick()
        waitTag("confirm_payment")
        compose.onNodeWithTag("mode_UPI").performClick()
        compose.onNodeWithTag("pay_amount").performTextClearance()
        compose.onNodeWithTag("pay_amount").performTextInput("300")
        shot("17_collect_partial")
        compose.onNodeWithTag("confirm_payment").performClick()
        waitTag("receipts_done")
        shot("18_payment_recorded")
        compose.onNodeWithTag("receipts_done").performClick()
        compose.onNodeWithTag("remind_all").performClick()
        waitTag("send_next")
        shot("19_bulk_remind")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("tab_history").performClick()
        waitTag("history_list")
        shot("20_payment_history")

        // Reports and expenses
        goTo("reports")
        waitTag("reports")
        shot("21_reports")
        compose.onNodeWithTag("reports").performTouchInput { swipeUp() }
        shot("22_reports_scrolled")
        goTo("about")
        waitTag("about")
        shot("22b_about")
        goTo("expenses")
        waitTag("expense_list")
        compose.onNodeWithTag("fab_expense").performClick()
        waitTag("expense_amount")
        compose.onNodeWithTag("expense_amount").performTextInput("2500")
        shot("23_add_expense")
        compose.onNodeWithTag("save_expense").performClick()
        compose.waitForIdle()
        shot("24_expenses")

        // Member card: freeze + calendar
        goTo("members")
        waitTag("member_list")
        compose.onNodeWithText("Rohit Kumar").performClick()
        waitTag("member_card")
        shot("25_member_card_history")
        compose.onNodeWithTag("card_freeze").performScrollTo().performClick()
        waitText("Freeze membership?")
        compose.onAllNodes(hasText("Freeze"))[compose.onAllNodes(hasText("Freeze")).fetchSemanticsNodes().size - 1].performClick()
        waitTag("card_resume")
        compose.onNodeWithTag("card_resume").performScrollTo()
        shot("26_member_frozen")
        compose.onNodeWithTag("card_calendar").performScrollTo().performClick()
        waitTag("calendar")
        shot("27_attendance_calendar")
        compose.onNodeWithTag("nav_back").performClick()
        waitTag("member_list")

        // Backup
        goTo("backup")
        compose.onNodeWithTag("create_backup").performClick()
        waitText("GymBackup_", 20_000)
        shot("28_backup")

        // Settings + dark mode
        goTo("settings")
        waitTag("settings_scroll")
        shot("29_settings")
        // Opening the menu while typing must release the text cursor
        compose.onNodeWithTag("field_gym_name").performClick()
        compose.onNodeWithTag("field_gym_name").assertIsFocused()
        compose.onNodeWithTag("nav_menu").performClick()
        waitTag("drawer_settings")
        compose.waitForIdle()
        compose.onNodeWithTag("field_gym_name").assertIsNotFocused()
        shot("29b_drawer_over_form")
        compose.onNodeWithTag("drawer_settings").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("theme_DARK").performScrollTo().performClick()
        shot("30_settings_bottom")
        compose.onNodeWithTag("save_settings").performClick()
        compose.waitForIdle()
        Thread.sleep(800)

        goTo("dashboard")
        waitTag("dashboard")
        shot("31_dashboard_dark")
        goTo("members")
        waitTag("member_list")
        compose.onNodeWithText("Priya Singh").performClick()
        waitTag("member_card")
        shot("32_member_card_dark")
        compose.onNodeWithContentDescription("Close").performClick()
        goTo("payments")
        waitTag("payments_list")
        shot("33_payments_dark")
        goTo("attendance")
        waitTag("present_summary")
        shot("34_attendance_dark")
        compose.onNodeWithTag("nav_menu").performClick()
        waitTag("drawer_members")
        shot("38_drawer_dark")
        compose.onNodeWithTag("drawer_members").performClick()
        waitTag("member_list")
        shot("39_master_list_dark")
        compose.onNodeWithTag("fab_add").performClick()
        waitTag("field_roll")
        shot("40_add_member_dark")
        compose.onNodeWithTag("nav_back").performClick()
        waitTag("member_list")
        goTo("reports")
        waitTag("reports")
        shot("41_reports_dark")
        goTo("expenses")
        waitTag("expense_list")
        shot("42_expenses_dark")
        goTo("about")
        waitTag("about")
        shot("43_about_dark")
        goTo("settings")
        waitTag("settings_scroll")
        shot("44_settings_dark")

        // Data checks
        runBlocking {
            val members = app.repository.activeMembers.first()
            assertTrue(members.any { it.rollNumber == "9" && it.fullName == "Karan Mehta" && it.shift == Shift.MORNING })
            val present = app.repository.attendanceFor(DateUtils.today(), Shift.MORNING)
            assertTrue(present.values.all { it })
            assertTrue(app.repository.snapshot().payments.any { it.amount == 300.0 && it.mode == "UPI" })
            assertTrue(app.repository.snapshot().expenses.any { it.amount == 2500.0 })
            assertTrue(app.repository.getMember(3)?.frozenFrom != null || members.any { it.frozenFrom != null })
        }
        assertTrue(compose.onAllNodes(hasTestTag("member_card")).fetchSemanticsNodes().isEmpty())

        // Render a receipt and an ID card for visual review
        runBlocking {
            val repo = app.repository
            val receiptNo = repo.snapshot().payments.maxByOrNull { it.id }!!.receiptNo
            saveBitmap("35_receipt", com.sushant.gymmanager.util.ReceiptRenderer.render(repo.receipt(receiptNo)!!, "en"))
            val m = repo.activeMembers.first().first()
            // PDF report: render every page to an image for review
            val pdf = java.io.File(ctx.cacheDir, "test_report.pdf")
            val snap = repo.snapshot()
            pdf.outputStream().use {
                com.sushant.gymmanager.util.ReportPdf.write(
                    com.sushant.gymmanager.util.ReportInput(
                        repo.getSettings().copy(gymPhone = "98765 43210", gymAddress = "Station Road, Biharsharif"),
                        java.time.YearMonth.now(), snap.payments, snap.expenses, snap.paymentCycles, snap.members
                    ), it
                )
            }
            android.graphics.pdf.PdfRenderer(android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                for (i in 0 until r.pageCount) {
                    r.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        saveBitmap("37_pdf_page${i + 1}", bmp)
                    }
                }
            }
            saveBitmap("36_id_card", com.sushant.gymmanager.util.IdCardRenderer.render(m, repo.getActiveMembership(m.id), repo.getSettings().copy(gymPhone = "98765 43210", gymAddress = "Station Road, Biharsharif"), "en"))
        }
    }
}

package com.sushant.gymmanager

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.gymmanager.data.local.AppDatabase
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.repository.CheckInResult
import com.sushant.gymmanager.data.repository.GymRepository
import com.sushant.gymmanager.data.repository.PlanInput
import com.sushant.gymmanager.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DataLogicTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /** A database created by app version 1.0 must open in 2.0 with all data kept. */
    @Test
    fun migrationFromVersion1KeepsData(): Unit = runBlocking {
        val name = "migration_test.db"
        ctx.deleteDatabase(name)
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            listOf(
                "CREATE TABLE IF NOT EXISTS `members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rollNumber` TEXT NOT NULL, `fullName` TEXT NOT NULL, `photoUri` TEXT, `dateOfBirth` TEXT NOT NULL, `whatsappNumber` TEXT NOT NULL, `dateOfJoining` TEXT NOT NULL, `fatherName` TEXT, `motherName` TEXT, `shift` TEXT NOT NULL, `fromTime` TEXT NOT NULL, `toTime` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_members_rollNumber` ON `members` (`rollNumber`)",
                "CREATE TABLE IF NOT EXISTS `memberships` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberId` INTEGER NOT NULL, `planType` TEXT NOT NULL, `durationMonths` INTEGER NOT NULL, `durationDays` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `expiryDate` TEXT NOT NULL, `fee` REAL NOT NULL, `autoRenew` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_memberships_memberId` ON `memberships` (`memberId`)",
                "CREATE TABLE IF NOT EXISTS `payment_cycles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberId` INTEGER NOT NULL, `membershipId` INTEGER, `cycleStart` TEXT NOT NULL, `cycleEnd` TEXT NOT NULL, `amount` REAL NOT NULL, `isPaid` INTEGER NOT NULL, `paidDate` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_payment_cycles_memberId` ON `payment_cycles` (`memberId`)",
                "CREATE INDEX IF NOT EXISTS `index_payment_cycles_isPaid` ON `payment_cycles` (`isPaid`)",
                "CREATE TABLE IF NOT EXISTS `attendance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `memberId` INTEGER NOT NULL, `attendanceDate` TEXT NOT NULL, `shift` TEXT NOT NULL, `isPresent` INTEGER NOT NULL, `timeSlot` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS `index_attendance_attendanceDate_shift` ON `attendance` (`attendanceDate`, `shift`)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_memberId_attendanceDate_shift` ON `attendance` (`memberId`, `attendanceDate`, `shift`)",
                "CREATE TABLE IF NOT EXISTS `gym_settings` (`id` INTEGER NOT NULL, `gymName` TEXT NOT NULL, `morningShiftStart` TEXT NOT NULL, `morningShiftEnd` TEXT NOT NULL, `eveningShiftStart` TEXT NOT NULL, `eveningShiftEnd` TEXT NOT NULL, `expiringSoonDays` INTEGER NOT NULL, `fee1Month` REAL NOT NULL, `fee3Months` REAL NOT NULL, `fee6Months` REAL NOT NULL, `fee12Months` REAL NOT NULL, `themeMode` TEXT NOT NULL, `biometricMasterEnabled` INTEGER NOT NULL, `biometricAppLaunch` INTEGER NOT NULL, `biometricAddMember` INTEGER NOT NULL, `biometricDeleteMember` INTEGER NOT NULL, `biometricRestoreBackup` INTEGER NOT NULL, `biometricResetData` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "INSERT INTO members VALUES (1, '1', 'Old Member', NULL, '', '9876543210', '2026-10-01', NULL, NULL, 'MORNING', '06:00', '07:00', 1, 0)",
                "INSERT INTO memberships VALUES (1, 1, '1_MONTH', 1, 0, '2026-10-01', '2026-11-01', 1000.0, 1, 1, 0)",
                "INSERT INTO payment_cycles VALUES (1, 1, 1, '2026-10-01', '2026-11-01', 1000.0, 1, '2026-10-02 10:00:00', NULL, 0)",
                "INSERT INTO payment_cycles VALUES (2, 1, 1, '2026-11-01', '2026-12-01', 1000.0, 0, NULL, NULL, 0)",
                "INSERT INTO attendance VALUES (1, 1, '2026-10-05', 'MORNING', 1, '6:00 AM – 7:00 AM', 0)",
                "INSERT INTO gym_settings VALUES (1, 'Old Gym', '05:30', '10:30', '16:00', '22:00', 7, 1000, 2700, 5000, 9000, 'SYSTEM', 0, 0, 0, 0, 0, 0)"
            ).forEach { db.execSQL(it) }
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
        val repo = GymRepository(ctx, room)
        val snap = repo.snapshot()
        assertEquals(1, snap.members.size)
        assertEquals("Old Gym", snap.settings.gymName)
        assertTrue(snap.settings.autoBackupEnabled)
        val paid = snap.paymentCycles.first { it.id == 1L }
        assertEquals(1000.0, paid.paidAmount, 0.01)
        assertEquals(0.0, snap.paymentCycles.first { it.id == 2L }.paidAmount, 0.01)
        assertEquals(1, snap.payments.size)
        assertEquals(1, snap.attendance.size)
        // v2 -> v3 columns arrive with safe defaults and the receipt counter starts after the old ledger
        assertEquals(0.0, paid.waivedAmount, 0.01)
        assertTrue(snap.settings.lastReceiptNo >= 1)
        room.close()
        ctx.deleteDatabase(name)
    }

    /** Undoing the latest payment must never let its receipt number be issued again. */
    @Test
    fun receiptNumbersNeverRepeatAfterUndo(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val a = repo.addMember(member("10", LocalDate.now()), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        val b = repo.addMember(member("11", LocalDate.now()), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        val first = repo.recordPayments(mapOf(repo.cyclesOf(a).single().id to 1000.0), PayMode.CASH).single().receiptNo
        val second = repo.recordPayments(mapOf(repo.cyclesOf(b).single().id to 500.0), PayMode.CASH).single().receiptNo
        repo.undoPayment(repo.paymentsFor(b).first().single().id)
        val third = repo.recordPayments(mapOf(repo.cyclesOf(b).single().id to 500.0), PayMode.CASH).single().receiptNo
        assertTrue(setOf(first, second, third).size == 3)
        assertTrue(GymRepository.parseReceiptNo(third)!! > GymRepository.parseReceiptNo(second)!!)
        db.close()
    }

    @Test
    fun waiveAndUndoWaive(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("12", LocalDate.now()), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        val c = repo.cyclesOf(id).single()
        repo.recordPayments(mapOf(c.id to 600.0), PayMode.CASH)
        repo.waive(c.id, 400.0, "Discount")
        var now = repo.cyclesOf(id).single()
        assertTrue(now.isPaid)
        assertEquals(0.0, now.balance, 0.01)
        repo.unwaive(c.id, 400.0)
        now = repo.cyclesOf(id).single()
        assertFalse(now.isPaid)
        assertEquals(400.0, now.balance, 0.01)
        db.close()
    }

    @Test
    fun allocateFillsOldestDueFirst(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("13", LocalDate.now().minusDays(40)), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        repo.checkAndGenerateAutoRenewalCycles()
        val dues = repo.cyclesOf(id)
        assertEquals(2, dues.size)
        repo.recordPayments(GymRepository.allocate(dues, 1300.0), PayMode.UPI)
        val after = repo.cyclesOf(id)
        assertTrue(after.first().isPaid)
        assertEquals(700.0, after.last().balance, 0.01)
        db.close()
    }

    @Test
    fun shiftWindowChangeMovesMembers(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("14", LocalDate.now()).copy(fromTime = "11:00", toTime = "12:00", shift = "EVENING"), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        assertEquals(1, repo.reassignShifts("05:00", "12:00"))
        assertEquals("MORNING", repo.getMember(id)!!.shift)
        assertEquals(0, repo.reassignShifts("05:00", "12:00"))
        db.close()
    }

    @Test
    fun bulkFeeChangesOnlyFutureUntouchedDues(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("15", LocalDate.now().minusDays(5)), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        assertEquals(1, repo.bulkChangeFee(PlanType.ONE_MONTH, 1200.0))
        assertEquals(1200.0, repo.getActiveMembership(id)!!.fee, 0.01)
        // The running due started before today, so it keeps its price
        assertEquals(1000.0, repo.cyclesOf(id).single().amount, 0.01)
        repo.renewMembership(id)
        assertEquals(1200.0, repo.cyclesOf(id).last().amount, 0.01)
        db.close()
    }

    private fun memoryRepo(): Pair<AppDatabase, GymRepository> {
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        return db to GymRepository(ctx, db)
    }

    private fun member(roll: String, joined: LocalDate) = MemberEntity(
        rollNumber = roll, fullName = "Member $roll", whatsappNumber = "9876543210",
        dateOfJoining = joined.toString(), fromTime = "06:00", toTime = "07:00"
    )

    @Test
    fun partialPaymentsAndUndo(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("1", LocalDate.now().minusDays(5)), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        val cycle = repo.cyclesOf(id).single()
        val r1 = repo.recordPayments(mapOf(cycle.id to 400.0), PayMode.UPI)
        assertEquals(1, r1.size)
        var c = repo.cyclesOf(id).single()
        assertEquals(600.0, c.balance, 0.01)
        assertFalse(c.isPaid)
        repo.recordPayments(mapOf(cycle.id to 600.0), PayMode.CASH)
        c = repo.cyclesOf(id).single()
        assertTrue(c.isPaid)
        val receipt = repo.receipt(r1.first().receiptNo)!!
        assertEquals(400.0, receipt.total, 0.01)
        val last = repo.paymentsFor(id).first().first()
        repo.undoPayment(last.id)
        c = repo.cyclesOf(id).single()
        assertFalse(c.isPaid)
        assertEquals(600.0, c.balance, 0.01)
        db.close()
    }

    @Test
    fun feeChangeRebuildsOnlyUnpaidCycles(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val joined = LocalDate.now().minusDays(40)
        val id = repo.addMember(member("2", joined), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        repo.checkAndGenerateAutoRenewalCycles()
        val cycles = repo.cyclesOf(id)
        assertEquals(2, cycles.size)
        repo.markPaid(listOf(cycles.first().id))
        val m = repo.getMember(id)!!
        repo.updateMember(m, PlanInput(PlanType.ONE_MONTH, 0, 0, 1500.0, true))
        val after = repo.cyclesOf(id)
        assertEquals(2, after.size)
        assertEquals(1000.0, after.first().amount, 0.01)
        assertEquals(1500.0, after.last().amount, 0.01)
        // Changing the joining date when nothing is paid moves the whole schedule
        val id2 = repo.addMember(member("3", LocalDate.now()), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        val newStart = LocalDate.now().minusDays(3).toString()
        repo.updateMember(repo.getMember(id2)!!.copy(dateOfJoining = newStart), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        assertEquals(newStart, repo.cyclesOf(id2).single().cycleStart)
        db.close()
    }

    @Test
    fun freezeSkipsRenewalAndResumeExtendsPlan(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("4", LocalDate.now().minusDays(10)), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        val before = repo.getActiveMembership(id)!!.expiryDate
        repo.freeze(id, LocalDate.now().minusDays(6).toString())
        repo.unfreeze(id)
        assertNull(repo.getMember(id)!!.frozenFrom)
        assertEquals(LocalDate.parse(before).plusDays(6).toString(), repo.getActiveMembership(id)!!.expiryDate)
        assertEquals(LocalDate.parse(before).plusDays(6).toString(), repo.cyclesOf(id).single().cycleEnd)
        db.close()
    }

    @Test
    fun qrCheckIn(): Unit = runBlocking {
        val (db, repo) = memoryRepo()
        val id = repo.addMember(member("5", LocalDate.now()), PlanInput(PlanType.ONE_MONTH, 0, 0, 1000.0, true))
        assertEquals(CheckInResult.OK, repo.checkIn(GymRepository.qrContent("5")).result)
        assertEquals(CheckInResult.ALREADY, repo.checkIn(GymRepository.qrContent("5")).result)
        assertEquals(CheckInResult.NOT_FOUND, repo.checkIn(GymRepository.qrContent("99")).result)
        assertTrue(repo.attendanceFor(DateUtils.today(), "MORNING")[id] == true)
        repo.freeze(id)
        assertEquals(CheckInResult.FROZEN, repo.checkIn("5").result)
        db.close()
    }
}

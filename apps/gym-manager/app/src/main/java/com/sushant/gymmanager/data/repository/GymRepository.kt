package com.sushant.gymmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sushant.gymmanager.data.local.AppDatabase
import com.sushant.gymmanager.data.local.AttendanceEntity
import com.sushant.gymmanager.data.local.ExpenseEntity
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.MembershipEntity
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PaymentEntity
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.PhotoStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

data class PlanInput(
    val planType: String,
    val customMonths: Int,
    val customDays: Int,
    val fee: Double,
    val autoRenew: Boolean
) {
    val durationMonths: Int get() = if (planType == PlanType.CUSTOM) customMonths else DateUtils.monthsFor(planType)
    val durationDays: Int get() = if (planType == PlanType.CUSTOM) customDays else 0
}

data class MemberWithPlan(val member: MemberEntity, val membership: MembershipEntity?)

data class MemberDetails(
    val member: MemberEntity,
    val membership: MembershipEntity?,
    val presentCount: Int,
    val recordCount: Int
)

data class ReceiptRef(val receiptNo: String, val memberId: Long, val total: Double)

data class ReceiptData(
    val settings: GymSettingsEntity,
    val member: MemberEntity,
    val receiptNo: String,
    val paidAt: String,
    val mode: String,
    val lines: List<Pair<PaymentCycleEntity, Double>>,
    val total: Double,
    val balanceDue: Double
)

enum class CheckInResult { OK, ALREADY, NOT_FOUND, INACTIVE, FROZEN }

data class CheckIn(val result: CheckInResult, val member: MemberEntity?)

data class WidgetStats(
    val gymName: String,
    val active: Int,
    val presentToday: Int,
    val expectedToday: Int,
    val pending: Double,
    val membersWithDues: Int,
    val expiring: Int
)

class GymRepository(private val context: Context, private val db: AppDatabase) {
    private val members = db.memberDao()
    private val memberships = db.membershipDao()
    private val cycles = db.cycleDao()
    private val payments = db.paymentDao()
    private val expenses = db.expenseDao()
    private val attendance = db.attendanceDao()
    private val settingsDao = db.settingsDao()
    private val renewalLock = Mutex()

    // ---------- Settings ----------
    val settings: Flow<GymSettingsEntity> = settingsDao.observe().map { it ?: GymSettingsEntity() }
    val settingsOrNull: Flow<GymSettingsEntity?> = settingsDao.observe()

    suspend fun ensureSettings() {
        if (settingsDao.get() == null) settingsDao.upsert(GymSettingsEntity())
    }

    suspend fun getSettings(): GymSettingsEntity = settingsDao.get() ?: GymSettingsEntity()
    suspend fun saveSettings(s: GymSettingsEntity) = settingsDao.upsert(s.copy(id = 1))

    // ---------- Streams ----------
    val activeMembers: Flow<List<MemberEntity>> = members.observeActive()
    val inactiveMembers: Flow<List<MemberEntity>> = members.observeInactive()
    val allMembers: Flow<List<MemberEntity>> = members.observeAll()
    val activeMemberships: Flow<List<MembershipEntity>> = memberships.observeActive()
    val unpaidCycles: Flow<List<PaymentCycleEntity>> = cycles.observeUnpaid()
    val allCycles: Flow<List<PaymentCycleEntity>> = cycles.observeAll()
    val allPayments: Flow<List<PaymentEntity>> = payments.observeAll()
    val allExpenses: Flow<List<ExpenseEntity>> = expenses.observeAll()

    val activeMembersWithPlans: Flow<List<MemberWithPlan>> =
        combine(activeMembers, activeMemberships) { list, plans -> attachPlans(list, plans) }

    val inactiveMembersWithPlans: Flow<List<MemberWithPlan>> =
        combine(inactiveMembers, activeMemberships) { list, plans -> attachPlans(list, plans) }

    private fun attachPlans(list: List<MemberEntity>, plans: List<MembershipEntity>): List<MemberWithPlan> {
        val byMember = plans.groupBy { it.memberId }.mapValues { (_, v) -> v.maxByOrNull { it.createdAt } }
        return list.map { MemberWithPlan(it, byMember[it.id]) }
    }

    fun memberDetails(id: Long): Flow<MemberDetails?> = combine(
        members.observeById(id),
        memberships.observeActiveFor(id),
        attendance.observePresentCount(id),
        attendance.observeRecordCount(id)
    ) { m, plan, present, total -> m?.let { MemberDetails(it, plan, present, total) } }

    fun paymentsFor(memberId: Long): Flow<List<PaymentEntity>> = payments.observeFor(memberId)
    fun attendanceForMember(memberId: Long): Flow<List<AttendanceEntity>> = attendance.observeForMember(memberId)
    fun observeMember(id: Long): Flow<MemberEntity?> = members.observeById(id)

    suspend fun getMember(id: Long): MemberEntity? = members.getById(id)
    suspend fun getActiveMembership(memberId: Long): MembershipEntity? = memberships.getActiveFor(memberId)
    suspend fun latestCycle(memberId: Long): PaymentCycleEntity? = cycles.getLatestFor(memberId)
    suspend fun cyclesOf(memberId: Long): List<PaymentCycleEntity> = cycles.getFor(memberId)

    suspend fun isRollTaken(roll: String, excludeId: Long): Boolean =
        roll.isNotBlank() && members.countWithRoll(roll.trim(), excludeId) > 0

    // ---------- Members ----------
    suspend fun addMember(member: MemberEntity, plan: PlanInput): Long = db.withTransaction {
        val memberId = members.insert(member.copy(id = 0, createdAt = System.currentTimeMillis()))
        val start = member.dateOfJoining.ifBlank { DateUtils.today() }
        val expiry = DateUtils.calculateExpiryDate(start, plan.planType, plan.customMonths, plan.customDays)
        val membershipId = memberships.insert(
            MembershipEntity(
                memberId = memberId, planType = plan.planType, durationMonths = plan.durationMonths,
                durationDays = plan.durationDays, startDate = start, expiryDate = expiry,
                fee = plan.fee, autoRenew = plan.autoRenew
            )
        )
        cycles.insert(PaymentCycleEntity(memberId = memberId, membershipId = membershipId, cycleStart = start, cycleEnd = expiry, amount = plan.fee))
        memberId
    }

    /**
     * Saves member info. When the plan, fee or joining date changes, every billing cycle that has no money
     * against it is rebuilt from the new details; cycles with (partial) payments are kept as history.
     */
    suspend fun updateMember(member: MemberEntity, plan: PlanInput) = db.withTransaction {
        val old = members.getById(member.id)
        members.update(member.copy(frozenFrom = old?.frozenFrom))
        if (old != null && old.photoUri != member.photoUri) PhotoStore.delete(old.photoUri)

        val current = memberships.getActiveFor(member.id)
        val hasPayments = cycles.getFor(member.id).any { it.paidAmount > 0 || it.waivedAmount > 0 }
        val start = member.dateOfJoining.ifBlank { DateUtils.today() }
        if (current == null) {
            memberships.insert(
                MembershipEntity(
                    memberId = member.id, planType = plan.planType, durationMonths = plan.durationMonths,
                    durationDays = plan.durationDays, startDate = start,
                    expiryDate = DateUtils.calculateExpiryDate(start, plan.planType, plan.customMonths, plan.customDays),
                    fee = plan.fee, autoRenew = plan.autoRenew
                )
            )
            rebuildCycles(member.id)
            return@withTransaction
        }
        val changed = current.planType != plan.planType || current.durationMonths != plan.durationMonths ||
            current.durationDays != plan.durationDays || current.fee != plan.fee ||
            current.autoRenew != plan.autoRenew || (!hasPayments && current.startDate != start)
        memberships.update(
            current.copy(
                planType = plan.planType, durationMonths = plan.durationMonths, durationDays = plan.durationDays,
                fee = plan.fee, autoRenew = plan.autoRenew,
                startDate = if (hasPayments) current.startDate else start
            )
        )
        if (changed) rebuildCycles(member.id)
    }

    private suspend fun rebuildCycles(memberId: Long, today: LocalDate = LocalDate.now()) {
        val member = members.getById(memberId) ?: return
        val plan = memberships.getActiveFor(memberId) ?: return
        val kept = cycles.getFor(memberId).filter { it.paidAmount > 0 || it.waivedAmount > 0 }
        cycles.deleteUntouchedFor(memberId)
        var newestEnd: String
        if (kept.isEmpty()) {
            val end = DateUtils.calculateExpiryDate(plan.startDate, plan.planType, plan.durationMonths, plan.durationDays)
            cycles.insert(PaymentCycleEntity(memberId = memberId, membershipId = plan.id, cycleStart = plan.startDate, cycleEnd = end, amount = plan.fee))
            newestEnd = end
        } else {
            newestEnd = kept.maxOf { it.cycleEnd }
        }
        if (plan.autoRenew && member.isActive && member.frozenFrom == null) {
            newestEnd = catchUp(member.id, plan, newestEnd, today)
        }
        memberships.update(plan.copy(expiryDate = newestEnd))
    }

    /** Adds consecutive cycles until the newest one covers today. Returns the newest cycle end. */
    private suspend fun catchUp(memberId: Long, plan: MembershipEntity, fromEnd: String, today: LocalDate): String {
        var newestEnd = fromEnd
        var guard = 0
        while (guard++ < 240) {
            val lastEnd = DateUtils.parseDate(newestEnd) ?: break
            if (today.isBefore(lastEnd)) break
            val nextEnd = DateUtils.calculateExpiryDate(newestEnd, plan.planType, plan.durationMonths, plan.durationDays)
            if (nextEnd <= newestEnd) break
            if (cycles.countStartingOn(memberId, newestEnd) == 0) {
                cycles.insert(PaymentCycleEntity(memberId = memberId, membershipId = plan.id, cycleStart = newestEnd, cycleEnd = nextEnd, amount = plan.fee))
            }
            newestEnd = nextEnd
        }
        return newestEnd
    }

    suspend fun setActive(id: Long, active: Boolean) = members.setActive(id, active)

    suspend fun deleteMember(id: Long) {
        val member = members.getById(id)
        db.withTransaction {
            attendance.deleteFor(id)
            payments.deleteFor(id)
            cycles.deleteFor(id)
            memberships.deleteFor(id)
            members.delete(id)
        }
        PhotoStore.delete(member?.photoUri)
    }

    suspend fun renewMembership(memberId: Long) = db.withTransaction {
        val plan = memberships.getActiveFor(memberId) ?: return@withTransaction
        val today = LocalDate.now()
        val expiry = DateUtils.parseDate(plan.expiryDate) ?: today
        val start = if (expiry.isBefore(today)) today.toString() else plan.expiryDate
        val end = DateUtils.calculateExpiryDate(start, plan.planType, plan.durationMonths, plan.durationDays)
        if (cycles.countStartingOn(memberId, start) == 0) {
            cycles.insert(PaymentCycleEntity(memberId = memberId, membershipId = plan.id, cycleStart = start, cycleEnd = end, amount = plan.fee))
        }
        memberships.update(plan.copy(expiryDate = end))
    }

    // ---------- Freeze ----------
    suspend fun freeze(memberId: Long, from: String = DateUtils.today()) = members.setFrozen(memberId, from)

    /** Ends a freeze and pushes the running period and plan expiry forward by the frozen days. */
    suspend fun unfreeze(memberId: Long, today: LocalDate = LocalDate.now()) = db.withTransaction {
        val member = members.getById(memberId) ?: return@withTransaction
        val from = DateUtils.parseDate(member.frozenFrom)
        if (from == null) {
            members.setFrozen(memberId, null)
            return@withTransaction
        }
        val days = ChronoUnit.DAYS.between(from, today).coerceAtLeast(0)
        val plan = memberships.getActiveFor(memberId)
        val latest = cycles.getLatestFor(memberId)
        if (days > 0) {
            if (latest != null && latest.cycleEnd >= from.toString()) {
                cycles.update(latest.copy(cycleEnd = LocalDate.parse(latest.cycleEnd).plusDays(days).toString()))
            }
            if (plan != null && plan.expiryDate >= from.toString()) {
                memberships.update(plan.copy(expiryDate = LocalDate.parse(plan.expiryDate).plusDays(days).toString()))
            }
        }
        members.setFrozen(memberId, null)
    }

    // ---------- Auto-renewal engine ----------
    suspend fun checkAndGenerateAutoRenewalCycles(today: LocalDate = LocalDate.now()): Int = renewalLock.withLock {
        var created = 0
        db.withTransaction {
            for (member in members.getActive()) {
                if (member.frozenFrom != null) continue
                val plan = memberships.getActiveFor(member.id) ?: continue
                if (!plan.autoRenew) continue
                var latest = cycles.getLatestFor(member.id)
                if (latest == null) {
                    val first = PaymentCycleEntity(memberId = member.id, membershipId = plan.id, cycleStart = plan.startDate, cycleEnd = plan.expiryDate, amount = plan.fee)
                    cycles.insert(first)
                    created++
                    latest = first
                }
                val before = cycles.getFor(member.id).size
                val newestEnd = catchUp(member.id, plan, latest.cycleEnd, today)
                created += cycles.getFor(member.id).size - before
                if (newestEnd > plan.expiryDate) memberships.update(plan.copy(expiryDate = newestEnd))
            }
        }
        created
    }

    // ---------- Payments ----------
    private fun receiptNo(n: Long) = "GM-" + String.format(Locale.ENGLISH, "%05d", n)

    /** Highest receipt number ever issued (settings counter, or the ledger for older data). */
    private suspend fun lastReceiptNumber(): Long {
        val fromLedger = payments.getAll().mapNotNull { parseReceiptNo(it.receiptNo) }.maxOrNull() ?: 0L
        return maxOf(getSettings().lastReceiptNo, fromLedger)
    }

    fun unpaidFor(memberId: Long): Flow<List<PaymentCycleEntity>> = cycles.observeUnpaidFor(memberId)

    /**
     * Records money against cycles. [amounts] maps cycleId -> amount paid now (may be less than the balance
     * for a partial payment). One receipt is produced per member.
     */
    suspend fun recordPayments(amounts: Map<Long, Double>, mode: String, note: String? = null): List<ReceiptRef> = db.withTransaction {
        val now = DateUtils.nowTimestamp()
        val list = cycles.getByIds(amounts.keys.toList())
        var counter = lastReceiptNumber() + 1
        val refs = mutableListOf<ReceiptRef>()
        for ((memberId, memberCycles) in list.groupBy { it.memberId }) {
            val receipt = receiptNo(counter++)
            var total = 0.0
            for (c in memberCycles.sortedBy { it.cycleStart }) {
                val pay = (amounts[c.id] ?: 0.0).coerceIn(0.0, c.balance)
                if (pay <= 0.0) continue
                payments.insert(
                    PaymentEntity(
                        cycleId = c.id, memberId = memberId, amount = pay, mode = mode, paidAt = now,
                        receiptNo = receipt, note = note?.ifBlank { null }
                    )
                )
                val paid = c.paidAmount + pay
                val full = c.settledWith(paid)
                cycles.update(c.copy(paidAmount = paid, isPaid = full, paidDate = if (full) now else c.paidDate, paymentMode = mode))
                total += pay
            }
            if (total > 0) refs += ReceiptRef(receipt, memberId, total)
        }
        if (refs.isNotEmpty()) {
            val used = refs.maxOf { parseReceiptNo(it.receiptNo) ?: 0L }
            settingsDao.upsert(getSettings().copy(id = 1, lastReceiptNo = maxOf(getSettings().lastReceiptNo, used)))
        }
        refs
    }

    /** Lets go of part or all of a due (discount, write-off or a due added by mistake). */
    suspend fun waive(cycleId: Long, amount: Double, reason: String) = db.withTransaction {
        val c = cycles.getById(cycleId) ?: return@withTransaction
        val add = amount.coerceIn(0.0, c.balance)
        if (add <= 0.0) return@withTransaction
        val waived = c.waivedAmount + add
        val full = c.settledWith(c.paidAmount, waived)
        cycles.update(
            c.copy(
                waivedAmount = waived, waiveReason = reason.trim().ifBlank { null } ?: c.waiveReason,
                isPaid = full, paidDate = if (full) (c.paidDate ?: DateUtils.nowTimestamp()) else c.paidDate
            )
        )
    }

    /** Puts a waived amount back as due (undo of [waive]). */
    suspend fun unwaive(cycleId: Long, amount: Double) = db.withTransaction {
        val c = cycles.getById(cycleId) ?: return@withTransaction
        val waived = (c.waivedAmount - amount).coerceAtLeast(0.0)
        val full = c.settledWith(c.paidAmount, waived)
        cycles.update(
            c.copy(
                waivedAmount = waived, waiveReason = if (waived > 0) c.waiveReason else null,
                isPaid = full, paidDate = if (full) c.paidDate else null
            )
        )
    }

    /** Pays the full balance of each cycle (quick "Mark as Paid"). */
    suspend fun markPaid(ids: List<Long>, mode: String = PayMode.CASH): List<ReceiptRef> {
        val map = cycles.getByIds(ids).associate { it.id to it.balance }
        return recordPayments(map, mode)
    }

    /** Reverses one transaction; the cycle becomes due again for that amount. */
    suspend fun undoPayment(paymentId: Long) = db.withTransaction {
        val p = payments.getById(paymentId) ?: return@withTransaction
        payments.delete(paymentId)
        val c = cycles.getById(p.cycleId) ?: return@withTransaction
        val paid = (c.paidAmount - p.amount).coerceAtLeast(0.0)
        val full = c.settledWith(paid)
        cycles.update(c.copy(paidAmount = paid, isPaid = full, paidDate = if (full) c.paidDate else null))
    }

    suspend fun receipt(receiptNo: String): ReceiptData? {
        val txs = payments.getByReceipt(receiptNo)
        if (txs.isEmpty()) return null
        val member = members.getById(txs.first().memberId) ?: return null
        val cyc = cycles.getByIds(txs.map { it.cycleId }).associateBy { it.id }
        val lines = txs.mapNotNull { t -> cyc[t.cycleId]?.let { it to t.amount } }.sortedBy { it.first.cycleStart }
        val balance = cycles.getFor(member.id).sumOf { it.balance }
        return ReceiptData(getSettings(), member, receiptNo, txs.first().paidAt, txs.first().mode, lines, txs.sumOf { it.amount }, balance)
    }

    // ---------- Expenses ----------
    suspend fun saveExpense(e: ExpenseEntity) = expenses.upsert(e)
    suspend fun deleteExpense(id: Long) = expenses.delete(id)

    // ---------- Bulk changes ----------
    /** Re-sorts every member into Morning/Evening after the shift windows change. Returns how many moved. */
    suspend fun reassignShifts(morningStart: String, morningEnd: String): Int = db.withTransaction {
        var moved = 0
        for (m in members.getAll()) {
            val shift = DateUtils.determineShift(m.fromTime, morningStart, morningEnd)
            if (shift != m.shift) {
                members.setShift(m.id, shift)
                moved++
            }
        }
        moved
    }

    /**
     * New fee for everyone on [planType]: the plan price changes, and dues that start from [fromDate] onwards
     * and have no money against them use the new fee. Running and past dues keep their price.
     */
    suspend fun bulkChangeFee(planType: String, fee: Double, fromDate: String = DateUtils.today()): Int = db.withTransaction {
        val plans = memberships.getAllActive().filter { it.planType == planType }
        val ids = plans.map { it.memberId }.toSet()
        plans.forEach { memberships.update(it.copy(fee = fee)) }
        cycles.getUntouchedFrom(fromDate).filter { it.memberId in ids }.forEach { cycles.update(it.copy(amount = fee)) }
        plans.size
    }

    /** Adds members from an import; rows whose roll number already exists are skipped. Returns added count. */
    suspend fun importMembers(rows: List<Pair<MemberEntity, PlanInput>>): Int {
        var added = 0
        for ((m, plan) in rows) {
            if (isRollTaken(m.rollNumber, 0)) continue
            addMember(m, plan)
            added++
        }
        return added
    }

    val lastSeen: Flow<List<com.sushant.gymmanager.data.local.LastSeen>> = attendance.observeLastSeen()

    // ---------- Attendance ----------
    fun attendanceForDate(date: String): Flow<List<AttendanceEntity>> = attendance.observeForDate(date)

    suspend fun attendanceFor(date: String, shift: String): Map<Long, Boolean> =
        attendance.getFor(date, shift).associate { it.memberId to it.isPresent }

    suspend fun saveAttendance(date: String, shift: String, marks: Map<Long, Boolean>, slots: Map<Long, String>) {
        val existing = attendance.getFor(date, shift).associateBy { it.memberId }
        val now = System.currentTimeMillis()
        attendance.upsertAll(marks.map { (memberId, present) ->
            AttendanceEntity(
                id = existing[memberId]?.id ?: 0, memberId = memberId, attendanceDate = date, shift = shift,
                isPresent = present, timeSlot = slots[memberId].orEmpty(), recordedAt = now
            )
        })
    }

    /** QR check-in: marks the member present for today in their own shift. */
    suspend fun checkIn(code: String, date: String = DateUtils.today()): CheckIn {
        val roll = parseQr(code) ?: return CheckIn(CheckInResult.NOT_FOUND, null)
        val m = members.getByRoll(roll) ?: return CheckIn(CheckInResult.NOT_FOUND, null)
        if (!m.isActive) return CheckIn(CheckInResult.INACTIVE, m)
        if (m.frozenFrom != null) return CheckIn(CheckInResult.FROZEN, m)
        val existing = attendance.getFor(date, m.shift).firstOrNull { it.memberId == m.id }
        if (existing?.isPresent == true) return CheckIn(CheckInResult.ALREADY, m)
        attendance.upsertAll(
            listOf(
                AttendanceEntity(
                    id = existing?.id ?: 0, memberId = m.id, attendanceDate = date, shift = m.shift, isPresent = true,
                    timeSlot = DateUtils.formatTimeSlot(m.fromTime, m.toTime)
                )
            )
        )
        return CheckIn(CheckInResult.OK, m)
    }

    // ---------- Widget / notification stats ----------
    suspend fun widgetStats(today: LocalDate = LocalDate.now()): WidgetStats {
        val s = getSettings()
        val active = members.getActive()
        val present = attendance.getForDate(today.toString()).filter { it.isPresent }.map { it.memberId }.toSet()
        val expected = active.filter { it.frozenFrom == null }
        val unpaid = cycles.getUnpaid()
        var expiring = 0
        for (m in expected) {
            val plan = memberships.getActiveFor(m.id) ?: continue
            if (DateUtils.isExpiringSoon(plan.expiryDate, s.expiringSoonDays, today)) expiring++
        }
        return WidgetStats(
            gymName = s.gymName,
            active = active.size,
            presentToday = expected.count { it.id in present },
            expectedToday = expected.size,
            pending = unpaid.sumOf { it.balance },
            membersWithDues = unpaid.map { it.memberId }.toSet().size,
            expiring = expiring
        )
    }

    // ---------- Whole-database operations ----------
    suspend fun snapshot(): BackupData = BackupData(
        settings = getSettings(),
        members = members.getAll(),
        memberships = memberships.getAll(),
        paymentCycles = cycles.getAll(),
        attendance = attendance.getAll(),
        payments = payments.getAll(),
        expenses = expenses.getAll()
    )

    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        clearTables()
        members.insertAll(data.members)
        memberships.insertAll(data.memberships)
        cycles.insertAll(data.paymentCycles)
        attendance.insertAll(data.attendance)
        payments.insertAll(data.payments)
        expenses.insertAll(data.expenses)
        settingsDao.upsert(data.settings.copy(id = 1))
    }

    private suspend fun clearTables() {
        attendance.deleteAll(); payments.deleteAll(); cycles.deleteAll(); memberships.deleteAll()
        members.deleteAll(); expenses.deleteAll(); settingsDao.deleteAll()
    }

    suspend fun resetAll() {
        db.withTransaction {
            clearTables()
            settingsDao.upsert(GymSettingsEntity())
        }
        PhotoStore.clearAll(context)
    }

    companion object {
        fun parseReceiptNo(r: String): Long? = r.removePrefix("GM-").toLongOrNull()

        /** Splits [amount] over [cycles], oldest first (partial payment across several dues). */
        fun allocate(cycles: List<PaymentCycleEntity>, amount: Double): Map<Long, Double> {
            var left = amount
            val out = LinkedHashMap<Long, Double>()
            for (c in cycles.sortedBy { it.cycleStart }) {
                if (left <= 0.005) break
                val pay = minOf(left, c.balance)
                if (pay > 0) {
                    out[c.id] = pay
                    left -= pay
                }
            }
            return out
        }

        const val QR_PREFIX = "GYMMGR|"
        fun qrContent(roll: String) = QR_PREFIX + roll
        fun parseQr(code: String): String? {
            val c = code.trim()
            return when {
                c.startsWith(QR_PREFIX) -> c.removePrefix(QR_PREFIX).ifBlank { null }
                c.isNotEmpty() && c.length <= 12 -> c
                else -> null
            }
        }
    }
}

data class BackupData(
    val settings: GymSettingsEntity,
    val members: List<MemberEntity>,
    val memberships: List<MembershipEntity>,
    val paymentCycles: List<PaymentCycleEntity>,
    val attendance: List<AttendanceEntity>,
    val payments: List<PaymentEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList()
)

package com.sushant.gymmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.sushant.gymmanager.data.local.AppDatabase
import com.sushant.gymmanager.data.local.AttendanceEntity
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.MembershipEntity
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.PhotoStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

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

class GymRepository(private val context: Context, private val db: AppDatabase) {
    private val members = db.memberDao()
    private val memberships = db.membershipDao()
    private val payments = db.paymentDao()
    private val attendance = db.attendanceDao()
    private val settingsDao = db.settingsDao()
    private val renewalLock = Mutex()

    // ---------- Settings ----------
    val settings: Flow<GymSettingsEntity> = settingsDao.observe().map { it ?: GymSettingsEntity() }

    /** Emits null until the first DB read finishes (used for the startup lock decision). */
    val settingsOrNull: Flow<GymSettingsEntity?> = settingsDao.observe()

    suspend fun ensureSettings() {
        if (settingsDao.get() == null) settingsDao.upsert(GymSettingsEntity())
    }

    suspend fun getSettings(): GymSettingsEntity = settingsDao.get() ?: GymSettingsEntity()

    suspend fun saveSettings(s: GymSettingsEntity) = settingsDao.upsert(s.copy(id = 1))

    // ---------- Members ----------
    val activeMembers: Flow<List<MemberEntity>> = members.observeActive()
    val inactiveMembers: Flow<List<MemberEntity>> = members.observeInactive()
    val activeMemberships: Flow<List<MembershipEntity>> = memberships.observeActive()
    val unpaidCycles: Flow<List<PaymentCycleEntity>> = payments.observeUnpaid()

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

    fun cyclesFor(memberId: Long): Flow<List<PaymentCycleEntity>> = payments.observeForMember(memberId)

    suspend fun getMember(id: Long): MemberEntity? = members.getById(id)
    suspend fun getActiveMembership(memberId: Long): MembershipEntity? = memberships.getActiveFor(memberId)
    suspend fun latestCycle(memberId: Long): PaymentCycleEntity? = payments.getLatestFor(memberId)

    suspend fun isRollTaken(roll: String, excludeId: Long): Boolean =
        roll.isNotBlank() && members.countWithRoll(roll.trim(), excludeId) > 0

    /** Adds a member plus their first membership and first unpaid billing cycle. */
    suspend fun addMember(member: MemberEntity, plan: PlanInput): Long = db.withTransaction {
        val memberId = members.insert(member.copy(id = 0, createdAt = System.currentTimeMillis()))
        val start = member.dateOfJoining.ifBlank { DateUtils.today() }
        val expiry = DateUtils.calculateExpiryDate(start, plan.planType, plan.customMonths, plan.customDays)
        val membershipId = memberships.insert(
            MembershipEntity(
                memberId = memberId,
                planType = plan.planType,
                durationMonths = plan.durationMonths,
                durationDays = plan.durationDays,
                startDate = start,
                expiryDate = expiry,
                fee = plan.fee,
                autoRenew = plan.autoRenew
            )
        )
        payments.insert(
            PaymentCycleEntity(
                memberId = memberId,
                membershipId = membershipId,
                cycleStart = start,
                cycleEnd = expiry,
                amount = plan.fee
            )
        )
        memberId
    }

    /** Updates member info and the active plan. The current unpaid first cycle follows plan changes. */
    suspend fun updateMember(member: MemberEntity, plan: PlanInput) = db.withTransaction {
        val old = members.getById(member.id)
        members.update(member)
        if (old != null && old.photoUri != member.photoUri) PhotoStore.delete(old.photoUri)

        val current = memberships.getActiveFor(member.id)
        if (current == null) {
            val start = member.dateOfJoining.ifBlank { DateUtils.today() }
            val expiry = DateUtils.calculateExpiryDate(start, plan.planType, plan.customMonths, plan.customDays)
            val id = memberships.insert(
                MembershipEntity(
                    memberId = member.id, planType = plan.planType, durationMonths = plan.durationMonths,
                    durationDays = plan.durationDays, startDate = start, expiryDate = expiry,
                    fee = plan.fee, autoRenew = plan.autoRenew
                )
            )
            payments.insert(PaymentCycleEntity(memberId = member.id, membershipId = id, cycleStart = start, cycleEnd = expiry, amount = plan.fee))
            return@withTransaction
        }
        val planChanged = current.planType != plan.planType ||
            current.durationMonths != plan.durationMonths || current.durationDays != plan.durationDays
        val latest = payments.getLatestFor(member.id)
        // The plan's expiry tracks the latest billing cycle; re-derive it when the duration changes.
        val newExpiry = if (planChanged) {
            val anchor = latest?.cycleStart ?: current.startDate
            DateUtils.calculateExpiryDate(anchor, plan.planType, plan.customMonths, plan.customDays)
        } else current.expiryDate
        memberships.update(
            current.copy(
                planType = plan.planType,
                durationMonths = plan.durationMonths,
                durationDays = plan.durationDays,
                fee = plan.fee,
                autoRenew = plan.autoRenew,
                expiryDate = newExpiry
            )
        )
        if (latest != null && !latest.isPaid) {
            payments.update(latest.copy(amount = plan.fee, cycleEnd = if (planChanged) newExpiry else latest.cycleEnd))
        }
    }

    suspend fun setActive(id: Long, active: Boolean) = members.setActive(id, active)

    suspend fun deleteMember(id: Long) {
        val member = members.getById(id)
        db.withTransaction {
            attendance.deleteFor(id)
            payments.deleteFor(id)
            memberships.deleteFor(id)
            members.delete(id)
        }
        PhotoStore.delete(member?.photoUri)
    }

    /** Starts a fresh period for a member whose membership has ended (or renews early). */
    suspend fun renewMembership(memberId: Long) = db.withTransaction {
        val plan = memberships.getActiveFor(memberId) ?: return@withTransaction
        val today = LocalDate.now()
        val expiry = DateUtils.parseDate(plan.expiryDate) ?: today
        val start = if (expiry.isBefore(today)) today.toString() else plan.expiryDate
        val end = DateUtils.calculateExpiryDate(start, plan.planType, plan.durationMonths, plan.durationDays)
        if (payments.countStartingOn(memberId, start) == 0) {
            payments.insert(PaymentCycleEntity(memberId = memberId, membershipId = plan.id, cycleStart = start, cycleEnd = end, amount = plan.fee))
        }
        memberships.update(plan.copy(expiryDate = end))
    }

    // ---------- Auto-renewal engine ----------
    /**
     * For each active member on an auto-renewing plan: whenever today has reached the end of their latest
     * billing cycle, a new unpaid cycle is created that starts where the last one ended. Loops so that
     * members who have been away for several periods are fully caught up. Returns how many cycles were created.
     */
    suspend fun checkAndGenerateAutoRenewalCycles(today: LocalDate = LocalDate.now()): Int = renewalLock.withLock {
        var created = 0
        db.withTransaction {
            for (member in members.getActive()) {
                val plan = memberships.getActiveFor(member.id) ?: continue
                if (!plan.autoRenew) continue
                var last = payments.getLatestFor(member.id)
                if (last == null) {
                    val first = PaymentCycleEntity(
                        memberId = member.id, membershipId = plan.id,
                        cycleStart = plan.startDate, cycleEnd = plan.expiryDate, amount = plan.fee
                    )
                    payments.insert(first)
                    created++
                    last = first
                }
                var guard = 0
                var newestEnd = last.cycleEnd
                while (guard++ < 240) {
                    val lastEnd = DateUtils.parseDate(newestEnd) ?: break
                    if (today.isBefore(lastEnd)) break
                    val nextStart = newestEnd
                    val nextEnd = DateUtils.calculateExpiryDate(nextStart, plan.planType, plan.durationMonths, plan.durationDays)
                    if (nextEnd <= nextStart) break // zero-length custom plan safety
                    if (payments.countStartingOn(member.id, nextStart) == 0) {
                        payments.insert(
                            PaymentCycleEntity(
                                memberId = member.id, membershipId = plan.id,
                                cycleStart = nextStart, cycleEnd = nextEnd, amount = plan.fee
                            )
                        )
                        created++
                    }
                    newestEnd = nextEnd
                }
                if (newestEnd != plan.expiryDate && newestEnd > plan.expiryDate) {
                    memberships.update(plan.copy(expiryDate = newestEnd))
                }
            }
        }
        created
    }

    // ---------- Payments ----------
    suspend fun markPaid(ids: List<Long>) {
        if (ids.isNotEmpty()) payments.markPaid(ids, DateUtils.nowTimestamp())
    }

    // ---------- Attendance ----------
    fun attendanceForDate(date: String): Flow<List<AttendanceEntity>> = attendance.observeForDate(date)

    suspend fun attendanceFor(date: String, shift: String): Map<Long, Boolean> =
        attendance.getFor(date, shift).associate { it.memberId to it.isPresent }

    suspend fun saveAttendance(date: String, shift: String, marks: Map<Long, Boolean>, slots: Map<Long, String>) {
        val existing = attendance.getFor(date, shift).associateBy { it.memberId }
        val now = System.currentTimeMillis()
        val rows = marks.map { (memberId, present) ->
            AttendanceEntity(
                id = existing[memberId]?.id ?: 0,
                memberId = memberId,
                attendanceDate = date,
                shift = shift,
                isPresent = present,
                timeSlot = slots[memberId].orEmpty(),
                recordedAt = now
            )
        }
        attendance.upsertAll(rows)
    }

    // ---------- Whole-database operations ----------
    suspend fun snapshot(): BackupData = BackupData(
        settings = getSettings(),
        members = members.getAll(),
        memberships = memberships.getAll(),
        paymentCycles = payments.getAll(),
        attendance = attendance.getAll()
    )

    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        attendance.deleteAll(); payments.deleteAll(); memberships.deleteAll(); members.deleteAll(); settingsDao.deleteAll()
        members.insertAll(data.members)
        memberships.insertAll(data.memberships)
        payments.insertAll(data.paymentCycles)
        attendance.insertAll(data.attendance)
        settingsDao.upsert(data.settings.copy(id = 1))
    }

    suspend fun resetAll() {
        db.withTransaction {
            attendance.deleteAll(); payments.deleteAll(); memberships.deleteAll(); members.deleteAll(); settingsDao.deleteAll()
            settingsDao.upsert(GymSettingsEntity())
        }
        PhotoStore.clearAll(context)
    }
}

data class BackupData(
    val settings: GymSettingsEntity,
    val members: List<MemberEntity>,
    val memberships: List<MembershipEntity>,
    val paymentCycles: List<PaymentCycleEntity>,
    val attendance: List<AttendanceEntity>
)

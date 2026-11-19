package com.sushant.gymmanager.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object Shift {
    const val MORNING = "MORNING"
    const val EVENING = "EVENING"
}

object PlanType {
    const val ONE_MONTH = "1_MONTH"
    const val THREE_MONTHS = "3_MONTHS"
    const val SIX_MONTHS = "6_MONTHS"
    const val TWELVE_MONTHS = "12_MONTHS"
    const val CUSTOM = "CUSTOM"
    val all = listOf(ONE_MONTH, THREE_MONTHS, SIX_MONTHS, TWELVE_MONTHS, CUSTOM)
}

object PayMode {
    const val CASH = "CASH"
    const val UPI = "UPI"
    const val CARD = "CARD"
    const val BANK = "BANK"
    val all = listOf(CASH, UPI, CARD, BANK)
}

object ExpenseCategory {
    val all = listOf("Rent", "Electricity", "Salary", "Equipment", "Maintenance", "Other")
}

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

@Serializable
@Entity(
    tableName = "members",
    indices = [Index(value = ["rollNumber"], unique = true)]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rollNumber: String,
    val fullName: String,
    val photoUri: String? = null,
    val dateOfBirth: String = "",
    val whatsappNumber: String,
    val dateOfJoining: String,
    val fatherName: String? = null,
    val motherName: String? = null,
    val shift: String = Shift.MORNING,
    val fromTime: String,
    val toTime: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Set while the membership is paused (yyyy-MM-dd the freeze started). */
    val frozenFrom: String? = null
)

@Serializable
@Entity(
    tableName = "memberships",
    indices = [Index(value = ["memberId"])]
)
data class MembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val planType: String,
    val durationMonths: Int = 1,
    val durationDays: Int = 0,
    val startDate: String,
    val expiryDate: String,
    val fee: Double,
    val autoRenew: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "payment_cycles",
    indices = [Index(value = ["memberId"]), Index(value = ["isPaid"])]
)
data class PaymentCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val membershipId: Long? = null,
    val cycleStart: String,
    val cycleEnd: String,
    val amount: Double,
    val isPaid: Boolean = false,
    val paidDate: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val paidAmount: Double = 0.0,
    val paymentMode: String? = null,
    /** Amount the owner let go (discount / write-off / mistaken due). */
    val waivedAmount: Double = 0.0,
    val waiveReason: String? = null
) {
    val balance: Double get() = (amount - paidAmount - waivedAmount).coerceAtLeast(0.0)

    /** True when nothing is left to pay (paid and/or waived). */
    fun settledWith(paid: Double, waived: Double = waivedAmount): Boolean = paid + waived >= amount - 0.005
}

/** One money transaction against a billing cycle (supports partial payments). */
@Serializable
@Entity(
    tableName = "payments",
    indices = [Index(value = ["memberId"]), Index(value = ["cycleId"]), Index(value = ["paidAt"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val memberId: Long,
    val amount: Double,
    val mode: String = PayMode.CASH,
    /** yyyy-MM-dd HH:mm:ss */
    val paidAt: String,
    val receiptNo: String = "",
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "expenses", indices = [Index(value = ["date"])])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val category: String,
    val amount: Double,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "attendance",
    indices = [
        Index(value = ["attendanceDate", "shift"]),
        Index(value = ["memberId", "attendanceDate", "shift"], unique = true)
    ]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val attendanceDate: String,
    val shift: String,
    val isPresent: Boolean = false,
    val timeSlot: String = "",
    val recordedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "gym_settings")
data class GymSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val gymName: String = "PowerFit Gym",
    val morningShiftStart: String = "05:30",
    val morningShiftEnd: String = "10:30",
    val eveningShiftStart: String = "16:00",
    val eveningShiftEnd: String = "22:00",
    val expiringSoonDays: Int = 7,
    val fee1Month: Double = 1000.0,
    val fee3Months: Double = 2700.0,
    val fee6Months: Double = 5000.0,
    val fee12Months: Double = 9000.0,
    val themeMode: String = ThemeMode.SYSTEM,
    val biometricMasterEnabled: Boolean = false,
    val biometricAppLaunch: Boolean = false,
    val biometricAddMember: Boolean = false,
    val biometricDeleteMember: Boolean = false,
    val biometricRestoreBackup: Boolean = false,
    val biometricResetData: Boolean = false,
    val language: String = "en",
    val autoBackupEnabled: Boolean = true,
    val dailyReminderEnabled: Boolean = true,
    val reminderHour: Int = 9,
    val gymPhone: String = "",
    val gymAddress: String = "",
    /** Last receipt number issued. Only ever goes up, so a number is never reused after an undo. */
    val lastReceiptNo: Long = 0
) {
    fun defaultFeeFor(planType: String): Double? = when (planType) {
        PlanType.ONE_MONTH -> fee1Month
        PlanType.THREE_MONTHS -> fee3Months
        PlanType.SIX_MONTHS -> fee6Months
        PlanType.TWELVE_MONTHS -> fee12Months
        else -> null
    }
}

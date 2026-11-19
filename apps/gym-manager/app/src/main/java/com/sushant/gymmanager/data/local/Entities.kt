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
    val createdAt: Long = System.currentTimeMillis()
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
    val biometricResetData: Boolean = false
) {
    fun defaultFeeFor(planType: String): Double? = when (planType) {
        PlanType.ONE_MONTH -> fee1Month
        PlanType.THREE_MONTHS -> fee3Months
        PlanType.SIX_MONTHS -> fee6Months
        PlanType.TWELVE_MONTHS -> fee12Months
        else -> null
    }
}

package com.sushant.gymmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ROLL_ORDER = "ORDER BY LENGTH(rollNumber), rollNumber COLLATE NOCASE"

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE isActive = 1 $ROLL_ORDER")
    fun observeActive(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE isActive = 0 $ROLL_ORDER")
    fun observeInactive(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members $ROLL_ORDER")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeById(id: Long): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: Long): MemberEntity?

    @Query("SELECT * FROM members WHERE rollNumber = :roll COLLATE NOCASE LIMIT 1")
    suspend fun getByRoll(roll: String): MemberEntity?

    @Query("SELECT * FROM members WHERE isActive = 1")
    suspend fun getActive(): List<MemberEntity>

    @Query("SELECT * FROM members")
    suspend fun getAll(): List<MemberEntity>

    @Query("SELECT COUNT(*) FROM members WHERE rollNumber = :roll COLLATE NOCASE AND id != :excludeId")
    suspend fun countWithRoll(roll: String, excludeId: Long): Int

    @Insert
    suspend fun insert(member: MemberEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Update
    suspend fun update(member: MemberEntity)

    @Query("UPDATE members SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE members SET frozenFrom = :from WHERE id = :id")
    suspend fun setFrozen(id: Long, from: String?)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM members")
    suspend fun deleteAll()
}

@Dao
interface MembershipDao {
    @Query("SELECT * FROM memberships WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<MembershipEntity>>

    @Query("SELECT * FROM memberships WHERE memberId = :memberId AND isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveFor(memberId: Long): Flow<MembershipEntity?>

    @Query("SELECT * FROM memberships WHERE memberId = :memberId AND isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveFor(memberId: Long): MembershipEntity?

    @Query("SELECT * FROM memberships")
    suspend fun getAll(): List<MembershipEntity>

    @Insert
    suspend fun insert(membership: MembershipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MembershipEntity>)

    @Update
    suspend fun update(membership: MembershipEntity)

    @Query("DELETE FROM memberships WHERE memberId = :memberId")
    suspend fun deleteFor(memberId: Long)

    @Query("DELETE FROM memberships")
    suspend fun deleteAll()
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM payment_cycles WHERE isPaid = 0 ORDER BY cycleStart")
    fun observeUnpaid(): Flow<List<PaymentCycleEntity>>

    @Query("SELECT * FROM payment_cycles ORDER BY cycleStart")
    fun observeAll(): Flow<List<PaymentCycleEntity>>

    @Query("SELECT * FROM payment_cycles WHERE isPaid = 0")
    suspend fun getUnpaid(): List<PaymentCycleEntity>

    @Query("SELECT * FROM payment_cycles WHERE id = :id")
    suspend fun getById(id: Long): PaymentCycleEntity?

    @Query("SELECT * FROM payment_cycles WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PaymentCycleEntity>

    @Query("SELECT * FROM payment_cycles WHERE memberId = :memberId ORDER BY cycleStart")
    suspend fun getFor(memberId: Long): List<PaymentCycleEntity>

    @Query("SELECT * FROM payment_cycles WHERE memberId = :memberId ORDER BY cycleEnd DESC LIMIT 1")
    suspend fun getLatestFor(memberId: Long): PaymentCycleEntity?

    @Query("SELECT COUNT(*) FROM payment_cycles WHERE memberId = :memberId AND cycleStart = :start")
    suspend fun countStartingOn(memberId: Long, start: String): Int

    @Query("SELECT * FROM payment_cycles")
    suspend fun getAll(): List<PaymentCycleEntity>

    @Insert
    suspend fun insert(cycle: PaymentCycleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PaymentCycleEntity>)

    @Update
    suspend fun update(cycle: PaymentCycleEntity)

    @Query("DELETE FROM payment_cycles WHERE memberId = :memberId AND paidAmount <= 0")
    suspend fun deleteUntouchedFor(memberId: Long)

    @Query("DELETE FROM payment_cycles WHERE memberId = :memberId")
    suspend fun deleteFor(memberId: Long)

    @Query("DELETE FROM payment_cycles")
    suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY paidAt DESC, id DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE memberId = :memberId ORDER BY paidAt DESC, id DESC")
    fun observeFor(memberId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE receiptNo = :receiptNo")
    suspend fun getByReceipt(receiptNo: String): List<PaymentEntity>

    @Query("SELECT * FROM payments")
    suspend fun getAll(): List<PaymentEntity>

    @Query("SELECT COALESCE(MAX(id), 0) FROM payments")
    suspend fun maxId(): Long

    @Insert
    suspend fun insert(p: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PaymentEntity>)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM payments WHERE memberId = :memberId")
    suspend fun deleteFor(memberId: Long)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Upsert
    suspend fun upsert(e: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE attendanceDate = :date")
    fun observeForDate(date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE attendanceDate = :date AND shift = :shift")
    suspend fun getFor(date: String, shift: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE attendanceDate = :date")
    suspend fun getForDate(date: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE memberId = :memberId ORDER BY attendanceDate")
    fun observeForMember(memberId: Long): Flow<List<AttendanceEntity>>

    @Query("SELECT COUNT(*) FROM attendance WHERE memberId = :memberId AND isPresent = 1")
    fun observePresentCount(memberId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance WHERE memberId = :memberId")
    fun observeRecordCount(memberId: Long): Flow<Int>

    @Query("SELECT * FROM attendance")
    suspend fun getAll(): List<AttendanceEntity>

    @Upsert
    suspend fun upsertAll(items: List<AttendanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AttendanceEntity>)

    @Query("DELETE FROM attendance WHERE memberId = :memberId")
    suspend fun deleteFor(memberId: Long)

    @Query("DELETE FROM attendance")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM gym_settings WHERE id = 1")
    fun observe(): Flow<GymSettingsEntity?>

    @Query("SELECT * FROM gym_settings WHERE id = 1")
    suspend fun get(): GymSettingsEntity?

    @Upsert
    suspend fun upsert(settings: GymSettingsEntity)

    @Query("DELETE FROM gym_settings")
    suspend fun deleteAll()
}

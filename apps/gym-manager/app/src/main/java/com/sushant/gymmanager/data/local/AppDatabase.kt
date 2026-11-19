package com.sushant.gymmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemberEntity::class,
        MembershipEntity::class,
        PaymentCycleEntity::class,
        AttendanceEntity::class,
        GymSettingsEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun membershipDao(): MembershipDao
    abstract fun cycleDao(): CycleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "gym_manager.db"

        /** v1 -> v2: payment ledger, partial payments, freeze, expenses and new settings. Keeps all data. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `members` ADD COLUMN `frozenFrom` TEXT")
                db.execSQL("ALTER TABLE `payment_cycles` ADD COLUMN `paidAmount` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `payment_cycles` ADD COLUMN `paymentMode` TEXT")
                db.execSQL("UPDATE `payment_cycles` SET `paidAmount` = `amount`, `paymentMode` = 'CASH' WHERE `isPaid` = 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`cycleId` INTEGER NOT NULL, `memberId` INTEGER NOT NULL, `amount` REAL NOT NULL, " +
                        "`mode` TEXT NOT NULL, `paidAt` TEXT NOT NULL, `receiptNo` TEXT NOT NULL, `note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_memberId` ON `payments` (`memberId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_cycleId` ON `payments` (`cycleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_paidAt` ON `payments` (`paidAt`)")
                db.execSQL(
                    "INSERT INTO `payments` (`cycleId`, `memberId`, `amount`, `mode`, `paidAt`, `receiptNo`, `note`, `createdAt`) " +
                        "SELECT `id`, `memberId`, `amount`, 'CASH', COALESCE(`paidDate`, `cycleEnd` || ' 00:00:00'), " +
                        "'R' || `id`, NULL, `createdAt` FROM `payment_cycles` WHERE `isPaid` = 1"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)")
                db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'en'")
                db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `autoBackupEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `dailyReminderEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `reminderHour` INTEGER NOT NULL DEFAULT 9")
                db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `gymPhone` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `gym_settings` ADD COLUMN `gymAddress` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

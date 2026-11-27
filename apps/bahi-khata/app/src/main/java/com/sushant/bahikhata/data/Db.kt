package com.sushant.bahikhata.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/* ---------------- Entities ----------------
 * All money is stored as Long paise. Account "net" = opening + inflows − outflows.
 * A credit card's net is negative while money is owed on it (outstanding = −net).
 */

object AccType { const val BANK = "BANK"; const val CARD = "CARD"; const val CASH = "CASH"; const val WALLET = "WALLET" }

object EType {
    const val EXPENSE = "EXPENSE"; const val INCOME = "INCOME"
    const val CARD_PAYMENT = "CARD_PAYMENT"      // accountId (bank/cash) -> toAccountId (card)
    const val CASH_WITHDRAWAL = "CASH_WITHDRAWAL" // accountId (bank) -> toAccountId (cash)
    const val ADJUSTMENT = "ADJUSTMENT"          // signed amount on accountId
    const val UDHAR_GAVE = "UDHAR_GAVE"          // money left accountId for a person (not spending)
    const val UDHAR_GOT = "UDHAR_GOT"            // money came back into accountId (not income)
    const val GOAL_SAVE = "GOAL_SAVE"            // money moved to a savings goal (not spending)
    const val TRANSFER = "TRANSFER"              // accountId -> toAccountId, both his own (not spending, not income)
    const val INVEST = "INVEST"                  // money moved into investments (SIP, mutual fund, FD…): not spending
}

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val bank: String = "",
    val last4: String = "",            // comma separated last-4 numbers that point to this account (card no, a/c no)
    val upiApps: String = "",          // comma separated UPI apps linked (bank accounts)
    val colorIdx: Int = 0,
    val openingPaise: Long = 0,        // cards: negative = owed at setup
    val limitPaise: Long = 0,
    val statementDay: Int = 0,
    val dueDay: Int = 0,
    val billPaise: Long = -1,          // from bill SMS / manual, -1 = compute
    val minDuePaise: Long = -1,
    val billDueEpochDay: Long = -1,
    val archived: Boolean = false,
    val sort: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "-1") val bankBalPaise: Long = -1,     // last balance the bank's own SMS showed
    @ColumnInfo(defaultValue = "0") val bankBalAt: Long = 0,          // when that SMS came
    @ColumnInfo(defaultValue = "-1") val availLimitPaise: Long = -1,  // cards: available limit from the bank's SMS
    @ColumnInfo(defaultValue = "0") val availLimitAt: Long = 0,
)

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,              // EXPENSE / INCOME
    val icon: String,
    val colorIdx: Int,
    val sort: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(tableName = "entries", indices = [Index("time"), Index("accountId"), Index("categoryId")])
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amountPaise: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val upiApp: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val tags: String = "",           // comma separated, without '#'
    val time: Long = System.currentTimeMillis(),
    val recurringId: Long? = null,
    val smsKey: String? = null,
    val personId: Long? = null,
    val goalId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val cancelled: Boolean = false,   // payment failed / reversed: kept for history, never counted
    val refundOf: Long? = null,                                       // a refund linked to the purchase it pays back
    val vpa: String? = null,                                          // UPI ID the money went to / came from (from the SMS)
)

@Serializable
@Entity(tableName = "attachments", indices = [Index("entryId")])
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val fileName: String,          // file inside filesDir/attachments
    val mime: String,
    val displayName: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "merchants")
data class Merchant(
    @PrimaryKey val key: String,   // lowercase cleaned name
    val display: String,
    val categoryId: Long?,
    val accountId: Long?,
    val uses: Int = 1,
    val lastUsed: Long = System.currentTimeMillis(),
    val kind: String = "EXPENSE",   // EXPENSE shops vs INCOME payers, so each step suggests the right names
)

@Serializable
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amountPaise: Long,
    val accountId: Long,
    val categoryId: Long?,
    val merchant: String?,
    val note: String?,
    val rule: String,              // DAILY WEEKLY MONTHLY YEARLY
    val nextEpochDay: Long,
    val active: Boolean = true,
)

@Serializable
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val openingPaise: Long = 0,    // positive = they owe me
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "udhar", indices = [Index("personId")])
data class Udhar(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val gave: Boolean,             // true = I gave (they owe me more)
    val amountPaise: Long,
    val accountId: Long? = null,
    val entryId: Long? = null,     // linked money entry (UDHAR_GAVE / UDHAR_GOT) or the split expense
    val note: String = "",
    val time: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,         // null = overall
    val limitPaise: Long,
)

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetPaise: Long,
    val targetEpochDay: Long = -1,
    val savedPaise: Long = 0,
    val colorIdx: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

object SmsStatus {
    const val PENDING = "PENDING"; const val LOOK = "LOOK"; const val ACCEPTED = "ACCEPTED"; const val IGNORED = "IGNORED"; const val INFO = "INFO"
    const val HISTORY = "HISTORY"   // older SMS read once for insights (subscriptions, payday); never shown as suggestions
}

@Serializable
@Entity(tableName = "sms_items", indices = [Index(value = ["key"], unique = true), Index("status")])
data class SmsItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,               // stable hash of sender+body+time(minute)
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val status: String,
    val kind: String,              // SmsKind name
    val amountPaise: Long = 0,
    val debit: Boolean = true,
    val last4: String = "",
    val accountId: Long? = null,
    val merchant: String = "",
    val upiApp: String = "",
    val ref: String = "",
    val balancePaise: Long = -1,
    val categoryId: Long? = null,
    val confidence: Int = 0,
    val entryId: Long? = null,
    val duplicateOf: Long? = null,
    val note: String = "",
    @ColumnInfo(defaultValue = "") val vpa: String = "",
    @ColumnInfo(defaultValue = "") val itemsText: String = "",   // "What did you buy?" answered before the entry was saved
    @ColumnInfo(defaultValue = "") val flags: String = "",       // comma list: SALARY, CHARGE, INVEST, TO:<accountId>, PERSON:<id>…
)

/** A UPI ID (or a shop name from SMS without one) he named once: later SMS use his name and category. */
@Serializable
@Entity(tableName = "upi_names")
data class UpiName(
    @PrimaryKey val key: String,         // "vpa:name@bank" or "name:raw shop text"
    val display: String,                 // empty = he chose not to name it (never asked again)
    val isPerson: Boolean = false,
    val categoryId: Long? = null,
    val personId: Long? = null,
    val askItems: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One thing bought in an entry ("What did you buy?"), with its price when he gave one. */
@Serializable
@Entity(tableName = "entry_items", indices = [Index("entryId"), Index("shopKey")])
data class EntryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val name: String,
    val pricePaise: Long = -1,           // total paid for this item (all of the quantity)
    val shopKey: String = "",            // lowercase shop name, for price history per shop
    @ColumnInfo(defaultValue = "1") val qty: Double = 1.0,
    @ColumnInfo(defaultValue = "") val unit: String = "",   // "", pcs, kg, g, L, ml, pack, dozen
    val time: Long = System.currentTimeMillis(),
)

/** Something coming up that an SMS told about: recharge expiry, a utility bill, an EMI or AutoPay debit. */
@Serializable
@Entity(tableName = "reminders", indices = [Index(value = ["key"], unique = true)])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val kind: String,                    // RECHARGE, UTILITY, EMI, AUTOPAY
    val title: String,
    val amountPaise: Long = -1,
    val dueEpochDay: Long,
    val accountId: Long? = null,
    val done: Boolean = false,
    val notified: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object RemindKind { const val RECHARGE = "RECHARGE"; const val UTILITY = "UTILITY"; const val EMI = "EMI"; const val AUTOPAY = "AUTOPAY" }

/** Processed SMS keys (also non-financial ones) so nothing is handled twice. */
@Entity(tableName = "sms_seen")
data class SmsSeen(@PrimaryKey val key: String, val time: Long)

/** Formats he taught by fixing a "Needs a look" item. */
@Serializable
@Entity(tableName = "learned")
data class Learned(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderKey: String,
    val shape: String,
    val debit: Boolean,
    val kind: String,
    val accountId: Long?,
    val merchant: String,
    val categoryId: Long?,
)

/* ---------------- DAOs ---------------- */

@Dao
interface MainDao {
    // accounts
    @Query("SELECT * FROM accounts ORDER BY archived, sort, id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts") suspend fun accountsNow(): List<Account>
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun account(id: Long): Account?
    @Upsert suspend fun upsertAccount(a: Account): Long
    @Delete suspend fun deleteAccount(a: Account)

    // categories
    @Query("SELECT * FROM categories ORDER BY kind, sort, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM categories") suspend fun categoriesNow(): List<Category>
    @Upsert suspend fun upsertCategory(c: Category): Long
    @Query("UPDATE entries SET categoryId=:to WHERE categoryId=:from") suspend fun moveCategory(from: Long, to: Long)
    @Delete suspend fun deleteCategory(c: Category)

    // entries
    @Query("SELECT * FROM entries ORDER BY time DESC, id DESC") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM entries") suspend fun entriesNow(): List<Entry>
    @Query("SELECT * FROM entries WHERE id=:id") suspend fun entry(id: Long): Entry?
    @Query("SELECT * FROM entries WHERE id=:id") fun entryFlow(id: Long): Flow<Entry?>
    @Upsert suspend fun upsertEntry(e: Entry): Long
    @Delete suspend fun deleteEntry(e: Entry)
    @Query("SELECT * FROM entries WHERE accountId=:acc AND amountPaise=:amt AND time BETWEEN :from AND :to")
    suspend fun similar(acc: Long, amt: Long, from: Long, to: Long): List<Entry>
    @Query("SELECT * FROM entries WHERE amountPaise=:amt AND time BETWEEN :from AND :to")
    suspend fun similarAny(amt: Long, from: Long, to: Long): List<Entry>

    // attachments
    @Query("SELECT * FROM attachments WHERE entryId=:entryId") fun attachments(entryId: Long): Flow<List<Attachment>>
    @Query("SELECT * FROM attachments") suspend fun attachmentsNow(): List<Attachment>
    @Query("SELECT entryId, COUNT(*) AS n FROM attachments GROUP BY entryId") fun attachmentCounts(): Flow<List<EntryCount>>
    @Insert suspend fun insertAttachment(a: Attachment): Long
    @Delete suspend fun deleteAttachment(a: Attachment)

    // merchants
    @Query("SELECT * FROM merchants WHERE `key`=:key") suspend fun merchant(key: String): Merchant?
    @Query("SELECT * FROM merchants ORDER BY uses DESC, lastUsed DESC") fun merchants(): Flow<List<Merchant>>
    @Query("SELECT * FROM merchants") suspend fun merchantsNow(): List<Merchant>
    @Upsert suspend fun upsertMerchant(m: Merchant)

    // recurring
    @Query("SELECT * FROM recurring ORDER BY nextEpochDay") fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring") suspend fun recurringNow(): List<Recurring>
    @Upsert suspend fun upsertRecurring(r: Recurring): Long
    @Delete suspend fun deleteRecurring(r: Recurring)

    // people / udhar
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE") fun people(): Flow<List<Person>>
    @Query("SELECT * FROM people") suspend fun peopleNow(): List<Person>
    @Query("SELECT * FROM people WHERE id=:id") suspend fun person(id: Long): Person?
    @Upsert suspend fun upsertPerson(p: Person): Long
    @Delete suspend fun deletePerson(p: Person)
    @Query("SELECT * FROM udhar ORDER BY time DESC, id DESC") fun udhar(): Flow<List<Udhar>>
    @Query("SELECT * FROM udhar") suspend fun udharNow(): List<Udhar>
    @Upsert suspend fun upsertUdhar(u: Udhar): Long
    @Delete suspend fun deleteUdhar(u: Udhar)
    @Query("DELETE FROM udhar WHERE personId=:pid") suspend fun deleteUdharOf(pid: Long)
    @Query("SELECT * FROM udhar WHERE entryId=:entryId") suspend fun udharOfEntry(entryId: Long): List<Udhar>

    // budgets / goals
    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun budgetsNow(): List<Budget>
    @Upsert suspend fun upsertBudget(b: Budget): Long
    @Delete suspend fun deleteBudget(b: Budget)
    @Query("SELECT * FROM goals ORDER BY createdAt") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun goalsNow(): List<Goal>
    @Upsert suspend fun upsertGoal(g: Goal): Long
    @Delete suspend fun deleteGoal(g: Goal)

    // sms
    @Query("SELECT * FROM sms_items WHERE status IN (:st) ORDER BY receivedAt DESC") fun sms(st: List<String>): Flow<List<SmsItem>>
    @Query("SELECT * FROM sms_items") suspend fun smsNow(): List<SmsItem>
    @Query("SELECT * FROM sms_items WHERE id=:id") suspend fun smsItem(id: Long): SmsItem?
    @Query("SELECT * FROM sms_items WHERE `key`=:key") suspend fun smsByKey(key: String): SmsItem?
    @Query("SELECT * FROM sms_items WHERE status IN ('PENDING','ACCEPTED') AND amountPaise=:amt AND receivedAt BETWEEN :from AND :to")
    suspend fun smsSimilar(amt: Long, from: Long, to: Long): List<SmsItem>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSms(s: SmsItem): Long
    @Update suspend fun updateSms(s: SmsItem)
    @Query("SELECT COUNT(*) FROM sms_items WHERE status=:st") fun smsCount(st: String): Flow<Int>
    @Query("SELECT EXISTS(SELECT 1 FROM sms_seen WHERE `key`=:key)") suspend fun seen(key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markSeen(s: SmsSeen)
    @Query("SELECT * FROM learned") suspend fun learned(): List<Learned>
    @Insert suspend fun insertLearned(l: Learned): Long

    // UPI names
    @Query("SELECT * FROM upi_names ORDER BY display COLLATE NOCASE") fun upiNames(): Flow<List<UpiName>>
    @Query("SELECT * FROM upi_names") suspend fun upiNamesNow(): List<UpiName>
    @Query("SELECT * FROM upi_names WHERE `key`=:key") suspend fun upiName(key: String): UpiName?
    @Upsert suspend fun upsertUpiName(u: UpiName)
    @Delete suspend fun deleteUpiName(u: UpiName)

    // items
    @Query("SELECT * FROM entry_items ORDER BY time DESC, id") fun items(): Flow<List<EntryItem>>
    @Query("SELECT * FROM entry_items") suspend fun itemsNow(): List<EntryItem>
    @Query("SELECT * FROM entry_items WHERE entryId=:entryId ORDER BY id") suspend fun itemsOf(entryId: Long): List<EntryItem>
    @Query("DELETE FROM entry_items WHERE entryId=:entryId") suspend fun deleteItemsOf(entryId: Long)
    @Insert suspend fun insertItem(i: EntryItem): Long

    // reminders
    @Query("SELECT * FROM reminders ORDER BY dueEpochDay") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminders") suspend fun remindersNow(): List<Reminder>
    @Query("SELECT * FROM reminders WHERE `key`=:key") suspend fun reminderByKey(key: String): Reminder?
    @Upsert suspend fun upsertReminder(r: Reminder): Long
    @Delete suspend fun deleteReminder(r: Reminder)

    @Query("SELECT * FROM sms_items WHERE status=:st ORDER BY receivedAt DESC") suspend fun smsWithStatus(st: String): List<SmsItem>

    @Query("DELETE FROM upi_names") suspend fun clearUpiNames()
    @Query("DELETE FROM entry_items") suspend fun clearItems()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("DELETE FROM merchants") suspend fun clearMerchants()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM people") suspend fun clearPeople()
    @Query("DELETE FROM udhar") suspend fun clearUdhar()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM sms_items") suspend fun clearSms()
    @Query("DELETE FROM learned") suspend fun clearLearned()
}

data class EntryCount(val entryId: Long, val n: Int)

@Database(
    entities = [Account::class, Category::class, Entry::class, Attachment::class, Merchant::class, Recurring::class,
        Person::class, Udhar::class, Budget::class, Goal::class, SmsItem::class, SmsSeen::class, Learned::class,
        UpiName::class, EntryItem::class, Reminder::class],
    version = 3,
    exportSchema = true,
    // 1 -> 2 (Bahi Khata 1.1): new tables and new columns with defaults; Room writes the SQL from the saved schemas,
    // so every entry, account and setting he already has stays exactly as it is
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MainDao

    companion object {
        const val NAME = "bahikhata.db"

        /** Encrypted with SQLCipher; the key is random and wrapped by an Android Keystore key. */
        fun open(context: Context, key: ByteArray? = DbKey.get(context)): AppDb {
            // never fall back to a plain database: no key means no database
            checkNotNull(key) { "database key unavailable" }
            // loading the encryption library is a one-time, required step before the first query
            android.os.StrictMode.allowThreadDiskReads().let { old -> try { System.loadLibrary("sqlcipher") } finally { android.os.StrictMode.setThreadPolicy(old) } }
            // the key is 32 random bytes, so it is handed to SQLCipher as a raw key (x'…'): no password stretching
            // (256,000 PBKDF2 rounds) on every app start — same strength, much faster cold start
            val raw = ("x'" + key.joinToString("") { "%02x".format(it) } + "'").toByteArray()
            return Room.databaseBuilder(context, AppDb::class.java, NAME).openHelperFactory(SupportOpenHelperFactory(raw)).build()
        }

        fun inMemory(context: Context): AppDb =
            Room.inMemoryDatabaseBuilder(context, AppDb::class.java).allowMainThreadQueries().build()
    }
}

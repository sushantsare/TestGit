package com.sushant.bahikhata

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sushant.bahikhata.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrade from 1.0.x (database version 1) to 1.1 (version 2): every row survives, new columns get their defaults,
 *  new tables exist. The version-1 tables are created exactly as Room's saved 1.json schema describes them. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val v1 = listOf(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `bank` TEXT NOT NULL, `last4` TEXT NOT NULL, `upiApps` TEXT NOT NULL, `colorIdx` INTEGER NOT NULL, `openingPaise` INTEGER NOT NULL, `limitPaise` INTEGER NOT NULL, `statementDay` INTEGER NOT NULL, `dueDay` INTEGER NOT NULL, `billPaise` INTEGER NOT NULL, `minDuePaise` INTEGER NOT NULL, `billDueEpochDay` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sort` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `icon` TEXT NOT NULL, `colorIdx` INTEGER NOT NULL, `sort` INTEGER NOT NULL, `archived` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, `categoryId` INTEGER, `upiApp` TEXT, `merchant` TEXT, `note` TEXT, `tags` TEXT NOT NULL, `time` INTEGER NOT NULL, `recurringId` INTEGER, `smsKey` TEXT, `personId` INTEGER, `goalId` INTEGER, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_entries_time` ON `entries` (`time`)",
            "CREATE INDEX IF NOT EXISTS `index_entries_accountId` ON `entries` (`accountId`)",
            "CREATE INDEX IF NOT EXISTS `index_entries_categoryId` ON `entries` (`categoryId`)",
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `mime` TEXT NOT NULL, `displayName` TEXT NOT NULL, `size` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_attachments_entryId` ON `attachments` (`entryId`)",
            "CREATE TABLE IF NOT EXISTS `merchants` (`key` TEXT NOT NULL, `display` TEXT NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `uses` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL, `kind` TEXT NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER, `merchant` TEXT, `note` TEXT, `rule` TEXT NOT NULL, `nextEpochDay` INTEGER NOT NULL, `active` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `openingPaise` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `udhar` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personId` INTEGER NOT NULL, `gave` INTEGER NOT NULL, `amountPaise` INTEGER NOT NULL, `accountId` INTEGER, `entryId` INTEGER, `note` TEXT NOT NULL, `time` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_udhar_personId` ON `udhar` (`personId`)",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER, `limitPaise` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetPaise` INTEGER NOT NULL, `targetEpochDay` INTEGER NOT NULL, `savedPaise` INTEGER NOT NULL, `colorIdx` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `sms_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `kind` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `debit` INTEGER NOT NULL, `last4` TEXT NOT NULL, `accountId` INTEGER, `merchant` TEXT NOT NULL, `upiApp` TEXT NOT NULL, `ref` TEXT NOT NULL, `balancePaise` INTEGER NOT NULL, `categoryId` INTEGER, `confidence` INTEGER NOT NULL, `entryId` INTEGER, `duplicateOf` INTEGER, `note` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_items_key` ON `sms_items` (`key`)",
            "CREATE INDEX IF NOT EXISTS `index_sms_items_status` ON `sms_items` (`status`)",
            "CREATE TABLE IF NOT EXISTS `sms_seen` (`key` TEXT NOT NULL, `time` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `learned` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `senderKey` TEXT NOT NULL, `shape` TEXT NOT NULL, `debit` INTEGER NOT NULL, `kind` TEXT NOT NULL, `accountId` INTEGER, `merchant` TEXT NOT NULL, `categoryId` INTEGER)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd2d889298b03659b771c1c69a6254459')"
    )

    @Test fun oneToTwoKeepsEverything() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null)
        v1.forEach { raw.execSQL(it) }
        raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd2d889298b03659b771c1c69a6254459')")
        raw.execSQL("INSERT INTO accounts (id,type,name,bank,last4,upiApps,colorIdx,openingPaise,limitPaise,statementDay,dueDay,billPaise,minDuePaise,billDueEpochDay,archived,sort,createdAt) VALUES (1,'BANK','SBI Savings','SBI','4417','GPay',0,1845000,0,0,0,-1,-1,-1,0,0,1)")
        raw.execSQL("INSERT INTO entries (id,type,amountPaise,accountId,toAccountId,categoryId,upiApp,merchant,note,tags,time,recurringId,smsKey,personId,goalId,createdAt) VALUES (1,'EXPENSE',38600,1,NULL,NULL,'GPay','Swiggy',NULL,'food',1700000000000,NULL,'k1',NULL,NULL,1)")
        raw.execSQL("INSERT INTO sms_items (id,`key`,sender,body,receivedAt,status,kind,amountPaise,debit,last4,accountId,merchant,upiApp,ref,balancePaise,categoryId,confidence,entryId,duplicateOf,note) VALUES (1,'k1','VM-SBIUPI-S','body',1700000000000,'ACCEPTED','DEBIT',38600,1,'4417',1,'Swiggy','GPay','',-1,NULL,100,1,NULL,'')")
        raw.version = 1
        raw.close()

        val db = Room.databaseBuilder(ctx, AppDb::class.java, name).allowMainThreadQueries().build()
        val dao = db.dao()
        val acc = dao.account(1)!!
        assertEquals("SBI Savings", acc.name); assertEquals(1_845_000L, acc.openingPaise)
        assertEquals(-1L, acc.bankBalPaise); assertEquals(0L, acc.bankBalAt); assertEquals(-1L, acc.availLimitPaise)
        val e = dao.entry(1)!!
        assertEquals("Swiggy", e.merchant); assertFalse(e.cancelled); assertNull(e.refundOf); assertNull(e.vpa)
        val s = dao.smsItem(1)!!
        assertEquals("", s.vpa); assertEquals("", s.flags); assertEquals("", s.itemsText)
        assertTrue(dao.upiNamesNow().isEmpty()); assertTrue(dao.itemsNow().isEmpty()); assertTrue(dao.remindersNow().isEmpty())
        dao.upsertUpiName(UpiName(key = "vpa:a@ybl", display = "A"))
        assertEquals("A", dao.upiName("vpa:a@ybl")!!.display)
        db.close(); ctx.deleteDatabase(name)
    }
}

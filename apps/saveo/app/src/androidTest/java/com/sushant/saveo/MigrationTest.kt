package com.sushant.saveo

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.saveo.data.SaveoDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Users of 1.0.0 must keep every saved item when updating to 1.1.0 (schema 1 → 2). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun v1ToV2KeepsDataAndMarksItVisible(): Unit = runBlocking {
        val name = "migrate-test.db"
        ctx.deleteDatabase(name)
        val f = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `saved_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hash` TEXT NOT NULL, `matchKey` TEXT NOT NULL, `mediaUri` TEXT NOT NULL, `kind` TEXT NOT NULL, `mime` TEXT NOT NULL, `source` TEXT NOT NULL, `originalName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `statusTime` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `binPath` TEXT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_items_hash` ON `saved_items` (`hash`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_items_matchKey` ON `saved_items` (`matchKey`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `seen` (`matchKey` TEXT NOT NULL, `firstSeen` INTEGER NOT NULL, PRIMARY KEY(`matchKey`))")
            db.execSQL("INSERT INTO saved_items (hash, matchKey, mediaUri, kind, mime, source, originalName, sizeBytes, durationMs, statusTime, savedAt, deletedAt, binPath) VALUES ('h1','a.jpg|10','content://media/external/images/media/1','IMAGE','image/jpeg','WHATSAPP','a.jpg',10,0,1,2,NULL,NULL)")
            db.execSQL("INSERT INTO saved_items (hash, matchKey, mediaUri, kind, mime, source, originalName, sizeBytes, durationMs, statusTime, savedAt, deletedAt, binPath) VALUES ('h2','b.mp4|20','content://media/external/video/media/2','VIDEO','video/mp4','BUSINESS','b.mp4',20,15000,3,4,5,'/bin/2.mp4')")
            db.execSQL("INSERT INTO seen (matchKey, firstSeen) VALUES ('a.jpg|10', 1)")
            db.version = 1
        }
        val room = Room.databaseBuilder(ctx, SaveoDb::class.java, name).addMigrations(SaveoDb.MIGRATION_1_2).build()
        try {
            val dao = room.dao()
            val saved = dao.allSaved()
            assertEquals(1, saved.size)
            assertTrue(saved.single().visible)
            assertEquals(1, dao.binOlderThan(Long.MAX_VALUE).size)
            assertTrue(dao.binOlderThan(Long.MAX_VALUE).single().visible)
            assertEquals(listOf("a.jpg|10"), dao.seenKeys())
            assertTrue(dao.hiddenIds().isEmpty())
        } finally { room.close(); ctx.deleteDatabase(name) }
    }
}

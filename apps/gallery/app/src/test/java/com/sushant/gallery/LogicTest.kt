package com.sushant.gallery

import androidx.compose.ui.geometry.Rect
import com.sushant.gallery.data.Dates
import com.sushant.gallery.data.Formats
import com.sushant.gallery.data.Source
import com.sushant.gallery.data.Vault
import com.sushant.gallery.ui.editor.EditMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Random
import javax.crypto.spec.SecretKeySpec

class LogicTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(d: LocalDate) = d.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun groupLabels() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals("Today", Dates.groupLabel(ms(today), today, zone))
        assertEquals("Yesterday", Dates.groupLabel(ms(today.minusDays(1)), today, zone))
        assertEquals("Sat, 14 Nov", Dates.groupLabel(ms(LocalDate.of(2026, 11, 14)), today, zone))
        assertEquals("October 2026", Dates.groupLabel(ms(LocalDate.of(2026, 10, 1)), today, zone))
        assertEquals("Tue, 30 Dec 2025", Dates.groupLabel(ms(LocalDate.of(2025, 12, 30)), LocalDate.of(2026, 1, 5), zone))
    }

    @Test fun daysLeftNeverNegative() {
        val day = 86_400_000L
        assertEquals(30, Formats.daysLeft(0, 0))
        assertEquals(29, Formats.daysLeft(0, day + 5))
        assertEquals(0, Formats.daysLeft(0, 45 * day))
    }

    @Test fun uniqueNames() {
        val taken = setOf("a.jpg", "a (1).jpg", "noext")
        assertEquals("b.jpg", Formats.uniqueName("b.jpg") { it in taken })
        assertEquals("a (2).jpg", Formats.uniqueName("a.jpg") { it in taken })
        assertEquals("noext (1)", Formats.uniqueName("noext") { it in taken })
    }

    @Test fun durationsAndSizes() {
        assertEquals("0:24", Formats.duration(24_000))
        assertEquals("1:05", Formats.duration(65_000))
        assertEquals("1:00:01", Formats.duration(3_601_000))
        assertEquals("2.4 MB", Formats.size(2_400_000))
        assertEquals("512 B", Formats.size(512))
    }

    @Test fun sourceRoundTrip() {
        val list = listOf(Source.All, Source.Favourites, Source.Videos, Source.Vault, Source.External,
            Source.Folder("/storage/emulated/0/DCIM/Camera"), Source.Folder("/sdcard/My + Pics/100%"), Source.Search("IMG 2026|x"))
        list.forEach { assertEquals(it, Source.decode(it.encode())) }
    }

    @Test fun vaultChunksRoundTrip() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        for (size in listOf(0, 1, 1000, 1 shl 20, (1 shl 20) + 7, 2_600_000)) {
            val data = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }
            val enc = ByteArrayOutputStream(); Vault.encrypt(ByteArrayInputStream(data), enc, key)
            val bytes = enc.toByteArray()
            if (size > 16) assertTrue("ciphertext must differ", !bytes.copyOfRange(bytes.size - 16, bytes.size).contentEquals(data.copyOfRange(size - 16, size)))
            val dec = ByteArrayOutputStream(); Vault.decrypt(ByteArrayInputStream(bytes), dec, key)
            assertArrayEquals("size $size", data, dec.toByteArray())
        }
    }

    @Test(expected = Exception::class) fun vaultRejectsTampering() {
        val key = SecretKeySpec(ByteArray(32), "AES")
        val enc = ByteArrayOutputStream(); Vault.encrypt(ByteArrayInputStream(ByteArray(5000) { 7 }), enc, key)
        val b = enc.toByteArray(); b[b.size - 3] = (b[b.size - 3] + 1).toByte()
        Vault.decrypt(ByteArrayInputStream(b), ByteArrayOutputStream(), key)
    }

    @Test fun pinHashIsStable() {
        val salt = ByteArray(16) { 3 }
        assertArrayEquals(Vault.pbkdf2("1234", salt), Vault.pbkdf2("1234", salt))
        assertTrue(!Vault.pbkdf2("1234", salt).contentEquals(Vault.pbkdf2("1235", salt)))
    }

    @Test fun aspectCrops() {
        assertEquals(Rect(0f, 0f, 1f, 1f), EditMath.aspectCrop(null, 400, 300))
        val sq = EditMath.aspectCrop(1f, 400, 200)
        assertEquals(0.25f, sq.left, 1e-4f); assertEquals(0.75f, sq.right, 1e-4f); assertEquals(1f, sq.height, 1e-4f)
        val wide = EditMath.aspectCrop(16f / 9f, 1000, 1000)
        assertEquals(1f, wide.width, 1e-4f); assertEquals(0.5625f, wide.height, 1e-3f)
    }
}

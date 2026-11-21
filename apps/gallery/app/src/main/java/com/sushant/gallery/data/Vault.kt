package com.sushant.gallery.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hidden album. Files are encrypted with AES-256-GCM in 1 MB chunks (so big videos never need to sit in memory).
 * The data key is random and stored wrapped by an Android Keystore key; the PIN is stored only as a PBKDF2 hash.
 * Nothing here is included in backups (see backup rules).
 */
class Vault(private val ctx: Context, private val db: GalleryDb, private val media: MediaSource) {
    private val sp = ctx.getSharedPreferences("gallery_secure", Context.MODE_PRIVATE)
    val dir: File get() = File(ctx.filesDir, "vault").apply { mkdirs() }
    private val viewDir: File get() = File(ctx.cacheDir, "vaultview").apply { mkdirs() }
    private val rnd = SecureRandom()
    @Volatile private var key: SecretKey? = null

    // ---------- PIN ----------
    val hasPin: Boolean get() = sp.contains("pinHash")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        sp.edit().putString("pinSalt", b64(salt)).putString("pinHash", b64(pbkdf2(pin, salt)))
            .putInt("fails", 0).putLong("lockUntil", 0).apply()
    }

    /** Seconds the user must wait after 5 wrong PINs (0 = may try now). */
    fun waitSeconds(now: Long = System.currentTimeMillis()): Int = ((sp.getLong("lockUntil", 0) - now) / 1000).toInt().coerceAtLeast(0)

    fun checkPin(pin: String, now: Long = System.currentTimeMillis()): Boolean {
        if (waitSeconds(now) > 0) return false
        val salt = unb64(sp.getString("pinSalt", null) ?: return false)
        val ok = MessageDigest.isEqual(pbkdf2(pin, salt), unb64(sp.getString("pinHash", "")!!))
        if (ok) sp.edit().putInt("fails", 0).apply() else {
            val f = sp.getInt("fails", 0) + 1
            sp.edit().putInt("fails", f).putLong("lockUntil", if (f % 5 == 0) now + 30_000 else 0).apply()
        }
        return ok
    }

    // ---------- keys ----------
    private fun dataKey(): SecretKey {
        key?.let { return it }
        synchronized(this) {
            key?.let { return it }
            val wrapped = sp.getString("dataKey", null)
            val raw = if (wrapped == null) {
                val k = ByteArray(32).also { rnd.nextBytes(it) }
                sp.edit().putString("dataKey", wrap(k)).commit(); k
            } else unwrap(wrapped)
            return SecretKeySpec(raw, "AES").also { key = it }
        }
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return gen.generateKey()
    }

    private fun wrap(raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return b64(c.iv) + ":" + b64(c.doFinal(raw))
    }

    private fun unwrap(s: String): ByteArray {
        val (iv, ct) = s.split(':').let { unb64(it[0]) to unb64(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return c.doFinal(ct)
    }

    fun lock() { key = null; viewDir.deleteRecursively() }

    // ---------- items ----------
    suspend fun hide(item: MediaItem): Boolean {
        val src = File(item.path)
        if (!src.exists()) return false
        val id = UUID.randomUUID().toString()
        val bin = File(dir, "$id.bin"); val thumb = File(dir, "$id.thumb")
        return runCatching {
            src.inputStream().use { i -> bin.outputStream().use { o -> encrypt(i, o, dataKey()) } }
            makeThumb(item)?.let { t -> t.inputStream().use { i -> thumb.outputStream().use { o -> encrypt(i, o, dataKey()) } } }
            db.vault().insert(
                VaultEntity(id, item.path, item.name, item.mime, item.isVideo, item.size, item.dateTaken, item.durationMs,
                    item.width, item.height, System.currentTimeMillis())
            )
            if (!src.delete()) error("could not remove original")
            if (item.id > 0) runCatching {
                val base = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ctx.contentResolver.delete(ContentUris.withAppendedId(base, item.id), null, null)
            }
            media.scan(listOf(item.path))
            true
        }.getOrElse {
            bin.delete(); thumb.delete(); db.vault().delete(id); false
        }
    }

    suspend fun unhide(e: VaultEntity): String? = runCatching {
        val orig = File(e.originalPath)
        val parent = (orig.parentFile ?: File(ctx.getExternalFilesDir(null), "Restored")).apply { mkdirs() }
        val dest = File(parent, Formats.uniqueName(orig.name) { File(parent, it).exists() })
        File(dir, "${e.id}.bin").inputStream().use { i -> dest.outputStream().use { o -> decrypt(i, o, dataKey()) } }
        dest.setLastModified(e.dateTaken)
        media.scan(listOf(dest.path))
        remove(e)
        dest.path
    }.getOrNull()

    suspend fun remove(e: VaultEntity) {
        File(dir, "${e.id}.bin").delete(); File(dir, "${e.id}.thumb").delete(); db.vault().delete(e.id)
    }

    fun thumbBytes(id: String): ByteArray? = readAll(File(dir, "$id.thumb")) ?: readAll(File(dir, "$id.bin"))
    fun fullBytes(id: String): ByteArray? = readAll(File(dir, "$id.bin"))

    /** Decrypted copy for video playback; removed when the album locks. */
    fun viewFile(e: VaultEntity): File? = runCatching {
        val out = File(viewDir, e.id + "." + e.name.substringAfterLast('.', "mp4"))
        if (!out.exists()) File(dir, "${e.id}.bin").inputStream().use { i -> out.outputStream().use { o -> decrypt(i, o, dataKey()) } }
        out
    }.getOrNull()

    private fun readAll(f: File): ByteArray? = runCatching {
        if (!f.exists()) return@runCatching null
        val bo = ByteArrayOutputStream(f.length().toInt().coerceAtLeast(1024))
        f.inputStream().use { decrypt(it, bo, dataKey()) }
        bo.toByteArray()
    }.getOrNull()

    private fun makeThumb(item: MediaItem): File? = runCatching {
        val bmp: Bitmap = if (item.isVideo) {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(item.path); r.getFrameAtTime(0) } finally { r.release() }
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(item.path, o)
            var s = 1; while (o.outWidth / (s * 2) >= 512 && o.outHeight / (s * 2) >= 512) s *= 2
            val b = BitmapFactory.decodeFile(item.path, BitmapFactory.Options().apply { inSampleSize = s })
            val deg = runCatching { ExifInterface(item.path).rotationDegrees }.getOrDefault(0)
            if (deg != 0 && b != null) Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true) else b
        } ?: return@runCatching null
        val f = File(ctx.cacheDir, "thumb_tmp.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        f
    }.getOrNull()

    companion object {
        private const val ALIAS = "gallery_vault_wrap"
        private const val CHUNK = 1 shl 20
        private val MAGIC = byteArrayOf('G'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), 1)

        fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
        fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

        fun pbkdf2(pin: String, salt: ByteArray): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

        /** Chunked AES-GCM: MAGIC, then repeated [len:int][iv:12][ciphertext+tag]. */
        fun encrypt(input: InputStream, output: OutputStream, key: SecretKey) {
            val out = DataOutputStream(output.buffered())
            out.write(MAGIC)
            val buf = ByteArray(CHUNK)
            val rnd = SecureRandom()
            while (true) {
                var n = 0
                while (n < CHUNK) { val r = input.read(buf, n, CHUNK - n); if (r < 0) break; n += r }
                if (n == 0) break
                val iv = ByteArray(12).also { rnd.nextBytes(it) }
                val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                val ct = c.doFinal(buf, 0, n)
                out.writeInt(n); out.write(iv); out.write(ct)
                if (n < CHUNK) break
            }
            out.flush()
        }

        fun decrypt(input: InputStream, output: OutputStream, key: SecretKey) {
            val inp = DataInputStream(input.buffered())
            val magic = ByteArray(MAGIC.size); inp.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Not a vault file" }
            val iv = ByteArray(12)
            while (true) {
                val n = try { inp.readInt() } catch (_: EOFException) { break }
                require(n in 1..CHUNK) { "Bad chunk" }
                inp.readFully(iv)
                val ct = ByteArray(n + 16); inp.readFully(ct)
                val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                output.write(c.doFinal(ct))
            }
            output.flush()
        }
    }
}

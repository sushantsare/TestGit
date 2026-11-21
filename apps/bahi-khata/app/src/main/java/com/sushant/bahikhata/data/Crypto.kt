package com.sushant.bahikhata.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Database / attachment key: 32 random bytes wrapped with an AES-GCM key that never leaves the Android Keystore. */
object DbKey {
    private const val ALIAS = "bahikhata_master"
    private const val FILE = "db.key"   // excluded from backups (see data_extraction_rules.xml)
    @Volatile private var cached: ByteArray? = null

    fun get(context: Context): ByteArray? = cached ?: synchronized(this) {
        cached ?: runCatching { load(context) }.getOrNull().also { cached = it }
    }

    private fun master(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return gen.generateKey()
    }

    private fun load(context: Context): ByteArray {
        val f = File(context.noBackupFilesDir, FILE)
        val master = master()
        if (f.exists()) {
            val parts = f.readText().split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, iv))
            return c.doFinal(ct)
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, master)
        val ct = c.doFinal(raw)
        f.writeText(Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP))
        return raw
    }
}

/** Password based AES-256-GCM used for backup files (PBKDF2, 120k rounds). */
object PassCrypto {
    private const val MAGIC = "BKB1"

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size > 32 && String(data.copyOfRange(0, 4)) == MAGIC) { "Not a Bahi Khata backup" }
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        return c.doFinal(data, 32, data.size - 32)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKey {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = f.generateSecret(PBEKeySpec(password, salt, 120_000, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }
}

/** Attachments are stored encrypted in app-private storage with the database key. */
object FileCrypto {
    fun encrypt(context: Context, plain: ByteArray): ByteArray {
        val key = DbKey.get(context) ?: return plain
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return byteArrayOf(0x42, 0x4B) + iv + c.doFinal(plain)
    }

    fun decrypt(context: Context, data: ByteArray): ByteArray {
        if (data.size < 30 || data[0] != 0x42.toByte() || data[1] != 0x4B.toByte()) return data
        val key = DbKey.get(context) ?: return data
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data.copyOfRange(2, 14)))
        return c.doFinal(data, 14, data.size - 14)
    }
}

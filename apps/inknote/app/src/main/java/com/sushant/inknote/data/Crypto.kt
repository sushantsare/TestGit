package com.sushant.inknote.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Encryption for locked notes (device key in Android Keystore) and PIN hashing / PIN-derived keys for backups. */
object Crypto {
    private const val ALIAS = "inknote_locked_notes"
    private const val HEADER = 0x454E4331 // "ENC1"

    private fun deviceKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return gen.generateKey()
    }

    fun isEncrypted(b: ByteArray?): Boolean = b != null && b.size > 16 &&
        ((b[0].toInt() and 0xff) shl 24 or ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)) == HEADER

    fun encrypt(plain: ByteArray, key: SecretKey = deviceKey()): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        if (key.algorithm == "AES" && key !is SecretKeySpec) c.init(Cipher.ENCRYPT_MODE, key)
        else { val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }; c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val iv = c.iv
        val body = c.doFinal(plain)
        return byteArrayOf(0x45, 0x4E, 0x43, 0x31, iv.size.toByte()) + iv + body
    }

    fun decrypt(data: ByteArray, key: SecretKey = deviceKey()): ByteArray {
        if (!isEncrypted(data)) return data
        val ivLen = data[4].toInt()
        val iv = data.copyOfRange(5, 5 + ivLen)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return c.doFinal(data, 5 + ivLen, data.size - 5 - ivLen)
    }

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun pbkdf2(pin: String, salt: ByteArray, bits: Int = 256, rounds: Int = 120_000): ByteArray {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return f.generateSecret(PBEKeySpec(pin.toCharArray(), salt, rounds, bits)).encoded
    }

    fun pinKey(pin: String, salt: ByteArray): SecretKey = SecretKeySpec(pbkdf2(pin, salt), "AES")

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

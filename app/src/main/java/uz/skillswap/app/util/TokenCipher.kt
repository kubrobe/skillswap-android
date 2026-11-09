package uz.skillswap.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the server session token before it is saved on the phone.
 *
 * The AES key lives in the Android Keystore: it is created inside secure hardware/OS storage
 * and can never be read out, not even by this app. So a copied SharedPreferences file is useless.
 * Saved format: "v1:<iv>:<encrypted token>" (Base64).
 */
object TokenCipher {
    private const val ALIAS = "skillswap_token_key"
    private const val PREFIX = "v1:"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /** Returns null if the saved value cannot be decrypted (e.g. the app data was copied to another phone). */
    fun decrypt(saved: String): String? = try {
        val parts = saved.removePrefix(PREFIX).split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

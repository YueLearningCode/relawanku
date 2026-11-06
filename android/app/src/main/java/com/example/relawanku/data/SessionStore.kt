package com.example.relawanku.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.relawanku.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only encrypted bearer tokens are persisted. Passwords and role choices are never stored. */
class SessionStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("session_secure", Context.MODE_PRIVATE)
    private val settings =
        context.applicationContext.getSharedPreferences("connection", Context.MODE_PRIVATE)
    val baseUrl: String
        get() =
            settings.getString("base_url", null)
                ?: runCatching { ServerAddress.normalize(BuildConfig.API_BASE_URL) }
                    .getOrDefault("https://example.invalid/api/")

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: String? = null
    val token: String?
        get() {
            if (!loaded) {
                synchronized(this) {
                    if (!loaded) {
                        cached = read()
                        loaded = true
                    }
                }
            }
            val expiry = prefs.getString("expires_at", null)
            if (expiry != null && (Dates.parse(expiry)?.time ?: 0) <= System.currentTimeMillis()) {
                clear()
                return null
            }
            return cached
        }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("relawanku_session_aes", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        "relawanku_session_aes",
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    private fun read(): String? =
        try {
            if (prefs.getString("origin", null) != baseUrl) null
            else {
                val encrypted = prefs.getString("encrypted", null)
                val iv = prefs.getString("iv", null)
                if (encrypted == null || iv == null) null
                else
                    Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(
                            Cipher.DECRYPT_MODE,
                            key(),
                            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
                        )
                        String(doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
                    }
            }
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            null
        }

    fun save(value: String, expiresAt: String?) {
        require(value.isNotBlank())
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs
            .edit()
            .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("origin", baseUrl)
            .putString("expires_at", expiresAt)
            .apply()
        cached = value
        loaded = true
    }

    fun clear() {
        cached = null
        loaded = true
        prefs.edit().clear().apply()
    }

    fun setBaseUrl(value: String) {
        val normalized = ServerAddress.normalize(value)
        if (normalized != baseUrl) {
            clear()
            settings.edit().putString("base_url", normalized).apply()
        }
    }
}

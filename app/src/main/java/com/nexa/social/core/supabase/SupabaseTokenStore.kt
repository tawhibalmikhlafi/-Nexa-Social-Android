package com.nexa.social.core.supabase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.supabaseSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "supabase_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

data class StoredSupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val userId: String,
    val email: String?
)

/**
 * Persists the Supabase session encrypted with a key that never leaves the Android Keystore.
 * The refresh token is the long-lived credential, so storing it as plaintext DataStore would be
 * equivalent to leaving a password on disk.
 */
class SupabaseTokenStore(context: Context) {

    private val appContext = context.applicationContext
    private val cipher = TokenCipher()
    private val gson = Gson()

    @Volatile
    private var cached: StoredSupabaseSession? = null

    fun currentSession(): StoredSupabaseSession? = cached

    suspend fun read(): StoredSupabaseSession? {
        val encoded = appContext.supabaseSessionDataStore.data
            .catch { emit(emptyPreferences()) }
            .first()[SESSION_KEY]
        if (encoded == null) {
            cached = null
            return null
        }
        val json = cipher.decrypt(encoded)
        if (json == null) {
            clear()
            return null
        }
        val session = runCatching {
            gson.fromJson(json, StoredSupabaseSession::class.java)
        }.getOrNull()?.takeIf { it.accessToken.isNotBlank() && it.userId.isNotBlank() }
        if (session == null) {
            clear()
            return null
        }
        cached = session
        return session
    }

    suspend fun save(session: StoredSupabaseSession): Boolean {
        val json = runCatching { gson.toJson(session) }.getOrNull() ?: return false
        val encoded = cipher.encrypt(json) ?: return false
        runCatching {
            appContext.supabaseSessionDataStore.edit { it[SESSION_KEY] = encoded }
        }.onFailure { return false }
        cached = session
        return true
    }

    suspend fun clear() {
        cached = null
        runCatching { appContext.supabaseSessionDataStore.edit { it.remove(SESSION_KEY) } }
    }

    private companion object {
        val SESSION_KEY = stringPreferencesKey("encrypted_session")
    }
}

private class TokenCipher {

    private val alias = "nexa_supabase_session_key"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        iv.copyInto(payload, destinationOffset = 1)
        encrypted.copyInto(payload, destinationOffset = 1 + iv.size)
        Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = payload[0].toInt()
        require(ivSize in 12..16 && payload.size > ivSize + 1)
        val iv = payload.copyOfRange(1, 1 + ivSize)
        val encrypted = payload.copyOfRange(1 + ivSize, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

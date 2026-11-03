package com.nexa.social.core.common

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Local-only password protection.
 *
 * Passwords are never stored in plain text. This is a development-grade salted SHA-256 hash,
 * NOT a server substitute: a real backend must use a dedicated password hashing algorithm
 * (for example Argon2/bcrypt) and never send or store these local hashes.
 */
object Security {

    private const val SALT_BYTES = 16
    private const val ITERATIONS = 10_000

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        var digest = password.toByteArray(Charsets.UTF_8) + saltBytes
        val sha = MessageDigest.getInstance("SHA-256")
        repeat(ITERATIONS) {
            sha.reset()
            digest = sha.digest(digest)
        }
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val actual = hashPassword(password, salt)
        return constantTimeEquals(actual, expectedHash)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.nexa.social.domain.model.AccountOrigin
import com.nexa.social.domain.model.AccountStatus
import com.nexa.social.domain.model.EmailVerificationStatus
import com.nexa.social.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseUserMapperTest {

    @Test
    fun `maps a complete profile row`() {
        val row = JsonObject().apply {
            addProperty("id", "3f1c2a7e-9d4b-4c1e-8f2a-1234567890ab")
            addProperty("username", "nexa_user")
            addProperty("display_name", "Nexa User")
            addProperty("bio", "hello")
            addProperty("avatar_url", "https://example.com/a.png")
            addProperty("cover_url", "https://example.com/c.png")
            addProperty("city", "Riyadh")
            addProperty("website", "https://nexa.social")
            addProperty("created_at", "2026-10-21T10:00:00Z")
            addProperty("followers_count", 12)
            addProperty("following_count", 7)
            addProperty("account_status", "ACTIVE")
            addProperty("account_origin", "REMOTE_ACCOUNT")
            addProperty("email_verification", "VERIFIED")
        }

        val user = requireNotNull(SupabaseUserMapper.toUser(row, email = "a@b.com"))

        assertEquals("3f1c2a7e-9d4b-4c1e-8f2a-1234567890ab", user.id)
        assertEquals("nexa_user", user.username)
        assertEquals("Nexa User", user.displayName)
        assertEquals("a@b.com", user.email)
        assertEquals("hello", user.bio)
        assertEquals(12, user.followersCount)
        assertEquals(7, user.followingCount)
        assertEquals(EmailVerificationStatus.VERIFIED, user.emailVerification)
        assertEquals(AccountStatus.ACTIVE, user.accountStatus)
        assertEquals(AccountOrigin.REMOTE_ACCOUNT, user.accountOrigin)
        assertEquals(1_792_576_800_000L, user.createdAt)
    }

    @Test
    fun `rejects rows without identity or username`() {
        assertNull(SupabaseUserMapper.toUser(JsonObject()))
        assertNull(
            SupabaseUserMapper.toUser(
                JsonObject().apply { addProperty("id", "id-without-username") }
            )
        )
    }

    @Test
    fun `defaults verification and counters when missing`() {
        val user = requireNotNull(
            SupabaseUserMapper.toUser(
                JsonObject().apply {
                    addProperty("id", "id")
                    addProperty("username", "someone")
                }
            )
        )

        assertEquals(0, user.followersCount)
        assertEquals(0, user.followingCount)
        assertEquals(EmailVerificationStatus.NOT_VERIFIED, user.emailVerification)
        assertEquals(AccountStatus.ACTIVE, user.accountStatus)
    }

    @Test
    fun `profile row never sends server-owned fields`() {
        val user = User(
            id = "3f1c2a7e-9d4b-4c1e-8f2a-1234567890ab",
            username = "Nexa_User",
            displayName = "Nexa User",
            email = "a@b.com",
            emailVerification = EmailVerificationStatus.VERIFIED,
            avatarUri = null,
            coverUri = null,
            bio = "",
            createdAt = 0L,
            accountStatus = AccountStatus.ACTIVE,
            accountOrigin = AccountOrigin.REMOTE_ACCOUNT,
            followersCount = 99,
            followingCount = 99
        )

        val row = SupabaseUserMapper.toProfileRow(user)

        assertEquals("nexa_user", row.get("username").asString)
        assertFalse(row.has("followers_count"))
        assertFalse(row.has("following_count"))
        assertFalse(row.has("account_status"))
        assertFalse(row.has("email_verification"))
        assertFalse(row.has("email"))
    }

    @Test
    fun `accepts offset timestamps`() {
        val row = JsonObject().apply {
            addProperty("id", "id")
            addProperty("username", "someone")
            addProperty("created_at", "2026-10-21T13:00:00+03:00")
        }

        val user = requireNotNull(SupabaseUserMapper.toUser(row))

        assertEquals(1_792_576_800_000L, user.createdAt)
        assertTrue(user.createdAt > 0L)
    }
}

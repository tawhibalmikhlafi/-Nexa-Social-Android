package com.nexa.social.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLinkParserTest {

    @Test
    fun `parses tokens from the uri fragment`() {
        val params = requireNotNull(
            AuthLinkParser.parse(
                "nexasocial://auth/callback#access_token=abc%2B1&refresh_token=ref_1&" +
                    "expires_in=3600&token_type=bearer&type=signup"
            )
        )

        assertEquals("abc+1", params.accessToken)
        assertEquals("ref_1", params.refreshToken)
        assertEquals("signup", params.type)
        assertTrue(params.hasSessionTokens)
        assertFalse(params.isRecovery)
        assertNull(params.error)
    }

    @Test
    fun `parses tokens from the query string`() {
        val params = requireNotNull(
            AuthLinkParser.parse(
                "nexasocial://auth/callback?access_token=abc&refresh_token=ref&type=recovery"
            )
        )

        assertTrue(params.hasSessionTokens)
        assertTrue(params.isRecovery)
    }

    @Test
    fun `reports provider errors without treating them as a session`() {
        val params = requireNotNull(
            AuthLinkParser.parse(
                "nexasocial://auth/callback#error=access_denied&" +
                    "error_description=Email+link+is+invalid+or+has+expired"
            )
        )

        assertEquals("access_denied", params.error)
        assertEquals("Email link is invalid or has expired", params.errorDescription)
        assertFalse(params.hasSessionTokens)
    }

    @Test
    fun `ignores links for other schemes hosts or paths`() {
        assertNull(AuthLinkParser.parse("https://auth/callback#access_token=a&refresh_token=b"))
        assertNull(AuthLinkParser.parse("nexasocial://other/callback#access_token=a&refresh_token=b"))
        assertNull(AuthLinkParser.parse("nexasocial://auth/elsewhere#access_token=a&refresh_token=b"))
        assertNull(AuthLinkParser.parse(null))
        assertNull(AuthLinkParser.parse("not a uri"))
    }

    @Test
    fun `callback path tolerates a trailing slash`() {
        val params = requireNotNull(
            AuthLinkParser.parse("nexasocial://auth/callback/?access_token=a&refresh_token=b")
        )

        assertTrue(params.hasSessionTokens)
    }

    @Test
    fun `missing tokens do not count as a session`() {
        val params = requireNotNull(
            AuthLinkParser.parse("nexasocial://auth/callback#access_token=only-access")
        )

        assertFalse(params.hasSessionTokens)
    }
}

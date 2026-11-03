package com.nexa.social.core.supabase

import com.nexa.social.core.common.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseHttpFailureTest {

    private val http = SupabaseHttp(SupabaseConfig("https://example.supabase.co", "test-key"))

    private fun reason(code: Int, body: String): FailureReason =
        http.failureReason(SupabaseHttp.HttpResponse(code, body))

    @Test
    fun `maps invalid credentials from GoTrue`() {
        assertEquals(
            FailureReason.INVALID_CREDENTIALS,
            reason(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""")
        )
    }

    @Test
    fun `maps unconfirmed email so the ui can explain it`() {
        assertEquals(
            FailureReason.EMAIL_NOT_VERIFIED,
            reason(400, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}""")
        )
    }

    @Test
    fun `maps missing schema to not configured`() {
        assertEquals(
            FailureReason.NOT_CONFIGURED,
            reason(404, """{"code":"PGRST205","message":"Could not find the table 'public.profiles'"}""")
        )
    }

    @Test
    fun `maps server errors to network`() {
        assertEquals(FailureReason.NETWORK, reason(503, "upstream unavailable"))
    }

    @Test
    fun `maps rate limits`() {
        assertEquals(
            FailureReason.TOO_MANY_REQUESTS,
            reason(429, """{"error_code":"over_email_send_rate_limit"}""")
        )
    }
}

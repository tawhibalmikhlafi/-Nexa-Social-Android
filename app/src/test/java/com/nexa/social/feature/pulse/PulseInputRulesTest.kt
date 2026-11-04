package com.nexa.social.feature.pulse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseInputRulesTest {
    @Test fun rejectsBlankEntryWithoutImage() {
        assertFalse(PulseInputRules.validEntry("  \n ", null))
    }

    @Test fun acceptsImageOnlyEntry() {
        assertTrue(PulseInputRules.validEntry("", "content://image/1"))
    }

    @Test fun enforcesEntryLimit() {
        assertTrue(PulseInputRules.validEntry("x".repeat(1000), null))
        assertFalse(PulseInputRules.validEntry("x".repeat(1001), null))
    }

    @Test fun enforcesCommentBounds() {
        assertFalse(PulseInputRules.validComment("  "))
        assertTrue(PulseInputRules.validComment("تعليق"))
        assertTrue(PulseInputRules.validComment("x".repeat(500)))
        assertFalse(PulseInputRules.validComment("x".repeat(501)))
    }
}

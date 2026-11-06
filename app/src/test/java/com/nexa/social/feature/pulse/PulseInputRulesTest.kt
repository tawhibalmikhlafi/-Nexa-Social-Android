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

    @Test fun acceptsVideoOnlyEntry() {
        assertTrue(PulseInputRules.validEntry("", null, "content://video/1"))
    }

    @Test fun rejectsImageAndVideoTogether() {
        assertFalse(PulseInputRules.validEntry("caption", "content://image/1", "content://video/1"))
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
        assertTrue(PulseInputRules.validComment("", "content://image/1"))
        assertTrue(PulseInputRules.validComment("", null, "content://video/1"))
        assertFalse(PulseInputRules.validComment("caption", "content://image/1", "content://video/1"))
    }
}

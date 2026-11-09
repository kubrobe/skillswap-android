package uz.skillswap.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.skillswap.app.util.Security
import uz.skillswap.app.util.Validators
import uz.skillswap.app.util.averageRating
import uz.skillswap.app.util.hasDuplicateSlots
import uz.skillswap.app.util.jitsiUrl
import uz.skillswap.app.util.nextOccurrence
import uz.skillswap.app.util.memberLevel
import uz.skillswap.app.util.nextLevelTarget
import uz.skillswap.app.util.slotLabel
import uz.skillswap.app.util.syncAgeText
import uz.skillswap.app.util.weightedRating

class UtilsTest {
    @Test fun slotLabel_formatsDayAndHour() {
        assertEquals("Mon 18:00", slotLabel(1, 18))
        assertEquals("Sun 09:00", slotLabel(7, 9))
    }

    @Test fun duplicateSlots_areDetected() {
        assertFalse(hasDuplicateSlots(listOf(1 to 18, 1 to 19, 2 to 18)))
        assertTrue(hasDuplicateSlots(listOf(1 to 18, 2 to 9, 1 to 18)))
    }

    @Test fun averageRating_roundsToOneDecimal() {
        assertEquals(0.0, averageRating(emptyList()), 0.0001)
        assertEquals(4.7, averageRating(listOf(5, 5, 4)), 0.0001)
    }

    @Test fun email_validation() {
        assertTrue(Validators.isValidEmail("aziza@example.uz"))
        assertFalse(Validators.isValidEmail("aziza@"))
        assertFalse(Validators.isValidEmail("no spaces@mail.uz"))
    }

    @Test fun password_rules() {
        assertTrue(Validators.isStrongPassword("skill2026"))
        assertFalse(Validators.isStrongPassword("short"))
        assertFalse(Validators.isStrongPassword("nodigitshere"))
    }

    @Test fun rating_mustBeFromOneToFive() {
        assertTrue(Validators.isValidRating(1))
        assertTrue(Validators.isValidRating(5))
        assertFalse(Validators.isValidRating(0))
        assertFalse(Validators.isValidRating(6))
    }

    @Test fun hash_isStable_andSalted() {
        val a = Security.hashPassword("secret1", "a@x.uz")
        assertEquals(a, Security.hashPassword("secret1", "a@x.uz"))
        assertNotEquals(a, Security.hashPassword("secret1", "b@x.uz"))
        assertEquals(64, a.length)
    }

    @Test fun weightedRating_countsEveryReview() {
        assertEquals(0.0, weightedRating(emptyList()), 0.0)
        // (5.0 x 1 review) and (4.0 x 3 reviews) = 17 / 4 = 4.25 -> 4.3 (one decimal)
        assertEquals(4.3, weightedRating(listOf(5.0 to 1, 4.0 to 3)), 0.0)
    }

    @Test fun memberLevel_followsFinishedLessons() {
        assertEquals("Newcomer", memberLevel(0))
        assertEquals("Bronze", memberLevel(1))
        assertEquals("Silver", memberLevel(3))
        assertEquals("Gold", memberLevel(5))
        assertEquals(2, nextLevelTarget(1)!!)
        assertTrue(nextLevelTarget(9) == null)
    }

    @Test fun syncAgeText_isShortAndFriendly() {
        val now = 10_000_000_000L
        assertEquals("not yet", syncAgeText(now, 0L))
        assertEquals("just now", syncAgeText(now, now - 20_000L))
        assertEquals("5 min ago", syncAgeText(now, now - 5 * 60_000L))
        assertEquals("2 h ago", syncAgeText(now, now - 125 * 60_000L))
        assertEquals("3 d ago", syncAgeText(now, now - 3 * 24 * 3_600_000L))
    }

    @Test fun nextOccurrence_findsTheNextWeeklyLesson() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val mondayTenAm = 1794218400000L                                  // Mon 9 Nov 2026, 10:00 UTC
        assertEquals(1794247200000L, nextOccurrence(mondayTenAm, 1, 18, utc))   // later today, 18:00
        assertEquals(1794819600000L, nextOccurrence(mondayTenAm, 1, 9, utc))    // 9:00 passed -> next Monday
        assertEquals(1794740400000L, nextOccurrence(mondayTenAm, 7, 11, utc))   // Sunday 15 Nov, 11:00
    }

    @Test fun jitsiUrl_isStableAndUnique() {
        assertEquals(jitsiUrl(5, 123456789L), jitsiUrl(5, 123456789L))
        assertNotEquals(jitsiUrl(5, 123456789L), jitsiUrl(6, 123456789L))
        assertTrue(jitsiUrl(5, 1L).startsWith("https://meet.jit.si/SkillSwap-5-"))
    }
}

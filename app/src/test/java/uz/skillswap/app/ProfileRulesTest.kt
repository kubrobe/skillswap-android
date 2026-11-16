package uz.skillswap.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.skillswap.app.data.User
import uz.skillswap.app.data.languageList
import uz.skillswap.app.util.Validators

/**
 * The rules the phone checks before it asks the server: @username, phone and Telegram username.
 * The server checks the same rules again (tests/profiles.test.js in the server repository), and the
 * contact-privacy rule lives only there: the phone never receives contacts it may not show.
 */
class ProfileRulesTest {

    @Test
    fun username_isCleaned_trimmedLowerCaseWithoutAt() {
        assertEquals("aziza_01", Validators.cleanUsername("  @Aziza_01 "))
        assertEquals("bobur", Validators.cleanUsername("BOBUR"))
        assertEquals("", Validators.cleanUsername("  "))
    }

    @Test
    fun username_validExamples() {
        for (ok in listOf("abc", "aziza", "aziza_k", "a23456789012345678", "a2345678901234567890", "@Timur", "x_1")) {
            assertTrue("\"$ok\" should be allowed", Validators.isValidUsername(ok))
        }
    }

    @Test
    fun username_lengthMustBe3To20() {
        assertEquals("short", Validators.usernameProblem(""))
        assertEquals("short", Validators.usernameProblem("ab"))
        assertNull(Validators.usernameProblem("abc"))
        assertNull(Validators.usernameProblem("a".repeat(20)))
        assertEquals("long", Validators.usernameProblem("a".repeat(21)))
    }

    @Test
    fun username_mustStartWithALetter() {
        assertEquals("start", Validators.usernameProblem("1aziza"))
        assertEquals("start", Validators.usernameProblem("_aziza"))
        assertEquals("start", Validators.usernameProblem("азиза"))        // Cyrillic is not a-z
    }

    @Test
    fun username_onlyLettersDigitsAndUnderscore() {
        for (bad in listOf("azi za", "aziza!", "azi-za", "aziza.k", "aziza@uz", "azizaй")) {
            assertEquals("\"$bad\"", "chars", Validators.usernameProblem(bad))
        }
    }

    @Test
    fun username_reservedNamesAreRefused_inAnyCase() {
        for (reserved in listOf("admin", "skillswap", "support", "Admin", "@SUPPORT", " SkillSwap ")) {
            assertFalse("\"$reserved\" is reserved", Validators.isValidUsername(reserved))
            assertEquals("reserved", Validators.usernameProblem(Validators.cleanUsername(reserved)))
        }
        assertTrue("only the exact names are reserved", Validators.isValidUsername("admin1"))
    }

    @Test
    fun phone_e164_plusAnd8To15Digits() {
        for (ok in listOf("+998901234567", "+998 90 123 45 67", "+998 (90) 123-45-67", "+12025550123", "+4915112345678", "")) {
            assertTrue("\"$ok\" should be allowed", Validators.isValidPhone(ok))
        }
        for (bad in listOf("998901234567", "901234567", "+0998901234567", "+99890", "+9989012345678901", "+99890abc4567", "call me")) {
            assertFalse("\"$bad\" should be refused", Validators.isValidPhone(bad))
        }
    }

    @Test
    fun phone_isCleaned_beforeItIsSent() {
        assertEquals("+998901234567", Validators.cleanPhone("+998 (90) 123-45-67"))
        assertEquals("", Validators.cleanPhone("   "))
    }

    @Test
    fun telegramUsername_acceptsAtAndLinks() {
        assertEquals("durov", Validators.cleanTelegramUsername("@durov"))
        assertEquals("durov", Validators.cleanTelegramUsername("https://t.me/durov"))
        assertEquals("durov", Validators.cleanTelegramUsername(" t.me/durov "))
        assertTrue(Validators.isValidTelegramUsername("@Aziza_K"))
        assertTrue("empty = no Telegram username", Validators.isValidTelegramUsername(""))
        assertFalse(Validators.isValidTelegramUsername("a b"))
        assertFalse(Validators.isValidTelegramUsername("1abc"))
        assertFalse(Validators.isValidTelegramUsername("abc"))
    }

    @Test
    fun languages_areStoredAsOneLine_andReadAsAList() {
        val user = User(id = 1, fullName = "Aziza", email = "a@b.uz", passwordHash = "", languages = "Uzbek, English,  Korean ,")
        assertEquals(listOf("Uzbek", "English", "Korean"), user.languageList())
        assertEquals(emptyList<String>(), user.copy(languages = "").languageList())
    }

    @Test
    fun contacts_areNullUntilTheServerSendsThem() {
        // A person as the phone stores them when the server hid the contacts: nothing to show, nothing to leak.
        val stranger = User(id = 2, fullName = "Bobur", email = "user2@hidden.skillswap", passwordHash = "", contactsHidden = true)
        assertNull(stranger.telegramUsername)
        assertNull(stranger.phone)
        assertTrue(stranger.contactsHidden)
    }
}

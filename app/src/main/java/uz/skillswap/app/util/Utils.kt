package uz.skillswap.app.util

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** day 1..7 + hour -> "Mon 18:00" */
fun slotLabel(day: Int, hour: Int): String =
    "${DAY_NAMES[(day - 1).coerceIn(0, 6)]} ${hour.toString().padStart(2, '0')}:00"

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(millis))

/** The same weekday + hour twice in one listing is a mistake. */
fun hasDuplicateSlots(slots: List<Pair<Int, Int>>): Boolean = slots.size != slots.toSet().size

/** Average of 1..5 ratings, one decimal. 0.0 when there are no ratings. */
fun averageRating(ratings: List<Int>): Double =
    if (ratings.isEmpty()) 0.0 else Math.round(ratings.average() * 10) / 10.0

/** Reputation of a teacher: every listing counts as many times as it has reviews. Pair = (rating, reviews). */
fun weightedRating(items: List<Pair<Double, Int>>): Double {
    val n = items.sumOf { it.second }
    return if (n == 0) 0.0 else Math.round(items.sumOf { it.first * it.second } / n * 10) / 10.0
}

/** Member level from finished lessons (learned + taught): 1 = Bronze, 2 = Silver, 5 = Gold. */
fun memberLevel(done: Int): String = when {
    done >= 5 -> "Gold"
    done >= 2 -> "Silver"
    done >= 1 -> "Bronze"
    else -> "Newcomer"
}

/** How many finished lessons are needed for the next level, or null at the top. */
fun nextLevelTarget(done: Int): Int? = when {
    done < 1 -> 1
    done < 2 -> 2
    done < 5 -> 5
    else -> null
}

object Validators {
    private val emailRegex = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")
    fun isValidEmail(email: String) = emailRegex.matches(email.trim())
    fun isStrongPassword(password: String) = password.length >= 6 && password.any { it.isDigit() }
    fun isValidRating(rating: Int) = rating in 1..5
}

object Security {
    /** SHA-256 hash with the e-mail as salt. Good for learning; use Firebase Auth / bcrypt in production. */
    fun hashPassword(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$password".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

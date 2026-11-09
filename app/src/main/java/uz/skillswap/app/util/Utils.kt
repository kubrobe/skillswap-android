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

/** "just now", "5 min ago", "3 h ago", "2 d ago" for the sync status pill. */
fun syncAgeText(nowMillis: Long, thenMillis: Long): String {
    if (thenMillis <= 0L) return "not yet"
    val minutes = (nowMillis - thenMillis).coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

/**
 * The next time a weekly lesson happens (day 1 = Monday ... 7 = Sunday, hour 0-23), in [zone].
 * Used for "Add to calendar". If the lesson is right now or already passed today, next week.
 */
fun nextOccurrence(nowMillis: Long, day: Int, hour: Int, zone: java.util.TimeZone): Long {
    val target = java.util.Calendar.getInstance(zone).apply {
        timeInMillis = nowMillis
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    // java.util.Calendar counts SUNDAY = 1 ... SATURDAY = 7; we count Monday = 1 ... Sunday = 7
    val wanted = if (day == 7) java.util.Calendar.SUNDAY else day + 1
    var addDays = (wanted - target.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
    if (addDays == 0 && target.timeInMillis <= nowMillis) addDays = 7
    target.add(java.util.Calendar.DAY_OF_MONTH, addDays)
    return target.timeInMillis
}

/** A free Jitsi video room for one booking. Hard to guess, the same on both phones. */
fun jitsiUrl(bookingId: Long, createdAt: Long): String =
    "https://meet.jit.si/SkillSwap-$bookingId-${(createdAt % 1_000_000_000L).toString(36)}"

/** Text inside the teacher's check-in QR code. */
fun checkinQrText(bookingId: Long, code: String): String = "skillswap:checkin:$bookingId:$code"

/** Reads a scanned QR. Returns the 4-digit code only if it is a SkillSwap check-in for THIS booking. */
fun parseCheckinQr(raw: String, bookingId: Long): String? {
    val parts = raw.trim().split(":")
    if (parts.size != 4 || parts[0] != "skillswap" || parts[1] != "checkin") return null
    if (parts[2].toLongOrNull() != bookingId) return null
    val code = parts[3]
    return if (code.length == 4 && code.all { it.isDigit() }) code else null
}

object Validators {
    private val emailRegex = Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")
    fun isValidEmail(email: String) = emailRegex.matches(email.trim())
    fun isStrongPassword(password: String) = password.length >= 8 && password.any { it.isDigit() }
    fun isValidRating(rating: Int) = rating in 1..5
}

object Security {
    /** SHA-256 hash with the e-mail as salt. Good for learning; use Firebase Auth / bcrypt in production. */
    fun hashPassword(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt:$password".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

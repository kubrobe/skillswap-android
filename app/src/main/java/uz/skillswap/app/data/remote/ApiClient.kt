package uz.skillswap.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uz.skillswap.app.data.Booking
import uz.skillswap.app.data.Offer
import uz.skillswap.app.data.Review
import uz.skillswap.app.data.Skill
import uz.skillswap.app.data.TimeSlot
import uz.skillswap.app.data.User
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The server answered with an error, e.g. 409 "Sorry, this time was just booked by someone else". */
class ApiException(val status: Int, message: String) : Exception(message)

/** No internet, or the server could not be reached. */
class OfflineException(message: String = "You are offline. Connect to the internet and try again.") :
    Exception(message)

/** Result of login / register: the session token and who logged in. */
class AuthResult(val token: String, val user: User)

/** One line of the time-bank history, e.g. +1 "taught" Python basics. */
class WalletLine(val amount: Int, val reason: String, val skillName: String, val createdAt: Long)

/** Skill credits: teach 1 hour = +1, learn 1 hour = -1. */
class Wallet(val balance: Int, val history: List<WalletLine>)

/** Someone who teaches what I want. theyWant != null = they also want something I teach (two-way swap). */
class Match(val offerId: Long, val teacherName: String, val skillName: String, val emoji: String, val theyWant: String?)

/** One line of the live community ticker, e.g. "Jasur taught Guitar · +1 🪙". [at] = epoch millis. */
class ActivityLine(val text: String, val at: Long)

/** Community numbers for the Impact screen (GET /impact, public). savedUzs is an ESTIMATE, see [assumption]. */
class Impact(
    val members: Int, val teachers: Int, val skills: Int,
    val lessonsDone: Int, val hoursShared: Int, val upcoming: Int,
    val credits: Int, val avgRating: Double, val reviews: Int,
    val tutorPriceUzs: Long, val savedUzs: Long, val assumption: String,
    val weekly: List<WeekCount>, val topSkills: List<TopSkill>
)
class WeekCount(val week: String, val lessons: Int)       // week = Monday, "2026-11-02"
class TopSkill(val name: String, val emoji: String, val lessons: Int)

/** One badge: unlocked when progress reaches goal. The id is fixed; the app shows its name in the user's language. */
class Badge(val id: String, val progress: Int, val goal: Int) {
    val unlocked: Boolean get() = progress >= goal
}
class LeaderRow(val id: Long, val name: String, val lessons: Int, val rating: Double, val emoji: String)
class Achievements(val badges: List<Badge>, val week: List<LeaderRow>, val allTime: List<LeaderRow>, val me: Long)

/** Telegram bot: is it set up on the server, and is this account linked to a chat. */
class TelegramStatus(val configured: Boolean, val linked: Boolean)

/** AI matchmaker answer: what it understood and up to 3 lessons, each with a reason. source = "ai" or "smart". */
class MatchResult(val source: String, val understood: String, val picks: List<MatchPick>)
class MatchPick(val offerId: Long, val reason: String)

/** Skill Galaxy: people (first names only) and lessons from teacher to learner. */
class Galaxy(val me: Long, val nodes: List<GalaxyNode>, val edges: List<GalaxyEdge>)
class GalaxyNode(val id: Long, val name: String, val category: String, val emoji: String, val taught: Int, val learned: Int)
class GalaxyEdge(val from: Long, val to: Long, val lessons: Int, val emoji: String)

/** Everything the app downloads in one request; the tables are written into Room as one transaction. */
class Snapshot(
    val me: User,
    val users: List<User>,
    val skills: List<Skill>,
    val offers: List<Offer>,
    val slots: List<TimeSlot>,
    val bookings: List<Booking>,
    val reviews: List<Review>,
    val wallet: Wallet = Wallet(0, emptyList()),
    val wants: List<Long> = emptyList(),
    val matches: List<Match> = emptyList(),
    val activity: List<ActivityLine> = emptyList()
)

/**
 * Talks to the SkillSwap server (the same Express + PostgreSQL backend as the website).
 * Uses only classes built into Android (HttpURLConnection + org.json), so no extra libraries.
 * Every call runs on Dispatchers.IO, never on the screen's main thread.
 */
class ApiClient(private val baseUrl: String = BASE_URL) {

    /** Wakes the free Render server up (it sleeps after 15 minutes without visitors). */
    suspend fun health(): Boolean = request("GET", "/health", null).optBoolean("ok", false)

    // ---------- accounts ----------
    suspend fun login(email: String, password: String): AuthResult =
        auth(request("POST", "/login", null, JSONObject().put("email", email).put("password", password)))

    suspend fun register(fullName: String, email: String, password: String): AuthResult =
        auth(request("POST", "/register", null,
            JSONObject().put("fullName", fullName).put("email", email).put("password", password)))

    /** Sign in with Google: the server checks Google's signed ID token and returns our own session token. */
    suspend fun googleLogin(idToken: String): AuthResult =
        auth(request("POST", "/google", null, JSONObject().put("idToken", idToken)))

    /** The Google client id the server uses (null = Google sign-in is not set up: hide the button). */
    suspend fun googleClientId(): String? =
        request("GET", "/config", null).optString("googleClientId", "").takeIf { it.isNotBlank() && it != "null" }

    suspend fun logout(token: String) {
        request("POST", "/logout", token)
    }

    suspend fun updateProfile(token: String, fullName: String, bio: String): User =
        user(request("POST", "/me", token, JSONObject().put("fullName", fullName).put("bio", bio))
            .getJSONObject("user"))

    // ---------- data ----------
    suspend fun sync(token: String): Snapshot {
        val o = request("GET", "/sync", token)
        return Snapshot(
            me = user(o.getJSONObject("user")),
            users = o.getJSONArray("users").objects().map { user(it) },
            skills = o.getJSONArray("skills").objects().map {
                Skill(id = it.getLong("id"), name = it.getString("name"),
                    category = it.getString("category"), emoji = it.getString("emoji"))
            },
            offers = o.getJSONArray("offers").objects().map {
                Offer(id = it.getLong("id"), userId = it.getLong("teacherId"), skillId = it.getLong("skillId"),
                    level = it.getString("level"), description = it.getString("description"),
                    trusted = it.optBoolean("trusted", false))
            },
            slots = o.getJSONArray("slots").objects().map {
                TimeSlot(id = it.getLong("id"), offerId = it.getLong("offerId"), day = it.getInt("day"),
                    hour = it.getInt("hour"), isBooked = it.getBoolean("isBooked"))
            },
            bookings = o.getJSONArray("bookings").objects().map {
                Booking(id = it.getLong("id"), learnerId = it.getLong("learnerId"), slotId = it.getLong("slotId"),
                    offerId = it.getLong("offerId"), status = it.getString("status"),
                    createdAt = it.getDouble("createdAt").toLong(),
                    checkinCode = if (it.isNull("checkinCode")) null else it.getString("checkinCode"))
            },
            reviews = o.getJSONArray("reviews").objects().map {
                Review(id = it.getLong("id"), bookingId = it.getLong("bookingId"), offerId = it.getLong("offerId"),
                    learnerName = it.getString("learnerName"), rating = it.getInt("rating"),
                    comment = it.getString("comment"), createdAt = it.getDouble("createdAt").toLong())
            },
            wallet = o.optJSONObject("wallet")?.let { w ->
                Wallet(
                    balance = w.optInt("balance", 0),
                    history = (w.optJSONArray("history") ?: JSONArray()).objects().map {
                        WalletLine(amount = it.getInt("amount"), reason = it.getString("reason"),
                            skillName = if (it.isNull("skillName")) "" else it.getString("skillName"),
                            createdAt = it.getDouble("createdAt").toLong())
                    }
                )
            } ?: Wallet(0, emptyList()),
            wants = o.optJSONArray("wants")?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList(),
            matches = (o.optJSONArray("matches") ?: JSONArray()).objects().map {
                Match(offerId = it.getLong("offerId"), teacherName = it.getString("teacherName"),
                    skillName = it.getString("skillName"), emoji = it.getString("emoji"),
                    theyWant = if (it.isNull("theyWant")) null else it.getString("theyWant"))
            },
            activity = (o.optJSONArray("activity") ?: JSONArray()).objects().map {
                ActivityLine(text = it.optString("text"), at = it.optDouble("at", 0.0).toLong())
            }
        )
    }

    /** Community impact: the same numbers for everyone, no login needed. */
    suspend fun impact(): Impact {
        val o = request("GET", "/impact", null)
        return Impact(
            members = o.optInt("members"), teachers = o.optInt("teachers"), skills = o.optInt("skills"),
            lessonsDone = o.optInt("lessonsDone"), hoursShared = o.optInt("hoursShared"), upcoming = o.optInt("upcoming"),
            credits = o.optInt("creditsInCirculation"), avgRating = o.optDouble("avgRating", 0.0), reviews = o.optInt("reviews"),
            tutorPriceUzs = o.optLong("tutorPriceUzs"), savedUzs = o.optLong("savedUzs"), assumption = o.optString("assumption"),
            weekly = (o.optJSONArray("weekly") ?: JSONArray()).objects().map { WeekCount(it.optString("week"), it.optInt("lessons")) },
            topSkills = (o.optJSONArray("topSkills") ?: JSONArray()).objects().map {
                TopSkill(it.optString("name"), it.optString("emoji", "🎓"), it.optInt("lessons"))
            }
        )
    }

    /** My badges and the top teachers this week / all time. */
    suspend fun achievements(token: String): Achievements {
        val o = request("GET", "/achievements", token)
        fun board(a: JSONArray?) = (a ?: JSONArray()).objects().map {
            LeaderRow(it.getLong("id"), it.optString("name"), it.optInt("lessons"), it.optDouble("rating", 0.0),
                if (it.isNull("emoji")) "🎓" else it.optString("emoji", "🎓"))
        }
        return Achievements(
            badges = (o.optJSONArray("badges") ?: JSONArray()).objects().map {
                Badge(it.getString("id"), it.optInt("progress"), it.optInt("goal", 1))
            },
            week = board(o.optJSONArray("week")),
            allTime = board(o.optJSONArray("allTime")),
            me = o.optLong("me")
        )
    }

    // ---------- Telegram bot ----------
    suspend fun telegramStatus(token: String): TelegramStatus {
        val o = request("GET", "/telegram", token)
        return TelegramStatus(o.optBoolean("configured"), o.optBoolean("linked"))
    }

    /** A one-time t.me link; opening it and tapping Start links the chat. */
    suspend fun telegramLink(token: String): String = request("POST", "/telegram/link", token).getString("url")

    suspend fun telegramUnlink(token: String) {
        request("POST", "/telegram/unlink", token)
    }

    /** "I want to learn guitar on weekends" -> the best lessons. [lang] = "en", "uz" or "ru". */
    suspend fun match(token: String, text: String, lang: String): MatchResult {
        val o = request("POST", "/match", token, JSONObject().put("text", text).put("lang", lang))
        return MatchResult(
            source = o.optString("source", "smart"),
            understood = o.optString("understood"),
            picks = (o.optJSONArray("picks") ?: JSONArray()).objects().map { MatchPick(it.getLong("offerId"), it.optString("reason")) }
        )
    }

    /** Who taught whom: the data for the Skill Galaxy screen. */
    suspend fun galaxy(token: String): Galaxy {
        val o = request("GET", "/galaxy", token)
        return Galaxy(
            me = o.optLong("me"),
            nodes = (o.optJSONArray("nodes") ?: JSONArray()).objects().map {
                GalaxyNode(it.getLong("id"), it.optString("name"), it.optString("category"),
                    it.optString("emoji", "🎓"), it.optInt("taught"), it.optInt("learned"))
            },
            edges = (o.optJSONArray("edges") ?: JSONArray()).objects().map {
                GalaxyEdge(it.getLong("from"), it.getLong("to"), it.optInt("lessons", 1), it.optString("emoji", "🎓"))
            }
        )
    }

    suspend fun setWants(token: String, skillIds: List<Long>) {
        val arr = JSONArray()
        skillIds.forEach { arr.put(it) }
        request("POST", "/wants", token, JSONObject().put("skillIds", arr))
    }

    /** A 60-minute plan for a booked lesson. Returns (plan text, "ai" or "template"). */
    suspend fun lessonPlan(token: String, bookingId: Long): Pair<String, String> {
        val o = request("POST", "/bookings/$bookingId/plan", token)
        return o.getString("plan") to o.optString("source", "template")
    }

    /**
     * Live updates: keeps ONE HTTP connection open and calls [onEvent] for every server event
     * ("hello", "changed", "notice", "credit", "activity"). Returns when the connection closes; the caller reconnects.
     */
    suspend fun listen(token: String, onEvent: suspend (String, JSONObject) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val conn = try {
                URL("$baseUrl/events").openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw OfflineException()
            }
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000              // the server pings every 20 s; 60 s of silence = dead line
                conn.setRequestProperty("Accept", "text/event-stream")
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code == 401) throw ApiException(401, "Please log in first")
                if (code != 200) throw OfflineException()
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var type = "message"
                    val data = StringBuilder()
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        when {
                            line.isEmpty() -> {                 // an empty line ends one event
                                if (data.isNotEmpty()) {
                                    val json = try { JSONObject(data.toString()) } catch (e: JSONException) { JSONObject() }
                                    onEvent(type, json)
                                }
                                type = "message"
                                data.setLength(0)
                            }
                            line.startsWith("event:") -> type = line.substringAfter(':').trim()
                            line.startsWith("data:") -> data.append(line.substringAfter(':').trim())
                        }
                    }
                }
            } catch (e: IOException) {
                throw OfflineException()
            } finally {
                conn.disconnect()
            }
        }

    suspend fun publishOffer(
        token: String, skillId: Long, level: String, description: String, slots: List<Pair<Int, Int>>
    ): Long {
        val arr = JSONArray()
        slots.forEach { arr.put(JSONObject().put("day", it.first).put("hour", it.second)) }
        val body = JSONObject().put("skillId", skillId).put("level", level)
            .put("description", description).put("slots", arr)
        return request("POST", "/offers", token, body).getLong("offerId")
    }

    suspend fun book(token: String, slotId: Long): Long =
        request("POST", "/bookings", token, JSONObject().put("slotId", slotId)).getLong("bookingId")

    suspend fun cancel(token: String, bookingId: Long) {
        request("POST", "/bookings/$bookingId/cancel", token)
    }

    /** Finish a lesson with the teacher's check-in code (scanned from the QR or typed). */
    suspend fun complete(token: String, bookingId: Long, code: String) {
        request("POST", "/bookings/$bookingId/complete", token, JSONObject().put("code", code))
    }

    suspend fun review(token: String, bookingId: Long, rating: Int, comment: String): Long =
        request("POST", "/bookings/$bookingId/review", token,
            JSONObject().put("rating", rating).put("comment", comment)).getLong("reviewId")

    // ---------- plumbing ----------
    private fun auth(o: JSONObject) = AuthResult(o.getString("token"), user(o.getJSONObject("user")))

    private fun user(o: JSONObject) = User(
        id = o.getLong("id"), fullName = o.getString("fullName"), email = o.getString("email"),
        passwordHash = "",                      // the server keeps the bcrypt hash, the phone never needs it
        bio = o.optString("bio", "")
    )

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private suspend fun request(method: String, path: String, token: String?, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = try {
                URL(baseUrl + path).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw OfflineException()
            }
            try {
                conn.requestMethod = method
                conn.connectTimeout = 20_000
                conn.readTimeout = 75_000           // the free Render server can need about a minute to wake up
                conn.setRequestProperty("Accept", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code in 502..504) throw OfflineException("The server is waking up. Please try again in a minute.")
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val json = try {
                    if (text.isBlank()) JSONObject() else JSONObject(text)
                } catch (e: JSONException) {
                    JSONObject()
                }
                if (code !in 200..299) throw ApiException(code, json.optString("error", "Server error ($code)"))
                json
            } catch (e: IOException) {
                throw OfflineException()
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        const val BASE_URL = "https://skillswap-vqt7.onrender.com/api/mobile"
    }
}

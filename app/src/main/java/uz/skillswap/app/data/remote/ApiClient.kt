package uz.skillswap.app.data.remote

import kotlinx.coroutines.Dispatchers
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

/** Everything the app downloads in one request; it is written into Room as one transaction. */
class Snapshot(
    val me: User,
    val users: List<User>,
    val skills: List<Skill>,
    val offers: List<Offer>,
    val slots: List<TimeSlot>,
    val bookings: List<Booking>,
    val reviews: List<Review>
)

/**
 * Talks to the SkillSwap server (the same Express + PostgreSQL backend as the website).
 * Uses only classes built into Android (HttpURLConnection + org.json), so no extra libraries.
 * Every call runs on Dispatchers.IO, never on the screen's main thread.
 */
class ApiClient(private val baseUrl: String = BASE_URL) {

    // ---------- accounts ----------
    suspend fun login(email: String, password: String): AuthResult =
        auth(request("POST", "/login", null, JSONObject().put("email", email).put("password", password)))

    suspend fun register(fullName: String, email: String, password: String): AuthResult =
        auth(request("POST", "/register", null,
            JSONObject().put("fullName", fullName).put("email", email).put("password", password)))

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
                    level = it.getString("level"), description = it.getString("description"))
            },
            slots = o.getJSONArray("slots").objects().map {
                TimeSlot(id = it.getLong("id"), offerId = it.getLong("offerId"), day = it.getInt("day"),
                    hour = it.getInt("hour"), isBooked = it.getBoolean("isBooked"))
            },
            bookings = o.getJSONArray("bookings").objects().map {
                Booking(id = it.getLong("id"), learnerId = it.getLong("learnerId"), slotId = it.getLong("slotId"),
                    offerId = it.getLong("offerId"), status = it.getString("status"),
                    createdAt = it.getDouble("createdAt").toLong())
            },
            reviews = o.getJSONArray("reviews").objects().map {
                Review(id = it.getLong("id"), bookingId = it.getLong("bookingId"), rating = it.getInt("rating"),
                    comment = it.getString("comment"), createdAt = it.getDouble("createdAt").toLong())
            }
        )
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

    suspend fun complete(token: String, bookingId: Long) {
        request("POST", "/bookings/$bookingId/complete", token)
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

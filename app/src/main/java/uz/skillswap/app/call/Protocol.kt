package uz.skillswap.app.call

import org.json.JSONArray
import org.json.JSONObject

/*
 * The video-room signalling protocol (version 1), the same as the server's src/rooms.js.
 *
 * Phone -> server: POST /rooms/:id/signal  {id, to, type, payload}
 * Server -> phone: SSE  room-state, peer-joined, peer-left, signal, room-ended
 *
 * Only plain Kotlin + org.json here (no Android, no WebRTC), so all of it is covered by JVM unit tests.
 */

/** One person in a room. role = "teacher" or "learner". online = connected (or reconnecting) right now. */
data class Participant(val userId: Long, val name: String, val role: String, val online: Boolean = false) {
    val firstName: String get() = name.trim().substringBefore(' ').ifBlank { name }
}

/** A STUN/TURN server for WebRTC. TURN entries carry temporary credentials made by our server. */
data class IceServerConfig(val urls: List<String>, val username: String? = null, val credential: String? = null)

/** Answer of POST /bookings/:id/room. */
data class RoomInfo(
    val roomId: String,
    val bookingId: Long,
    val skill: String,
    val you: Participant,
    val participants: List<Participant>,
    val startedAt: Long,
    val expiresAt: Long,
    val iceServers: List<IceServerConfig>,
    val turn: Boolean
) {
    val peer: Participant? get() = participants.firstOrNull { it.userId != you.userId }
}

/** What a connection message carries. */
sealed interface SignalPayload {
    val wireType: String

    data class Offer(val sdp: String, val iceRestart: Boolean = false) : SignalPayload { override val wireType = "offer" }
    data class Answer(val sdp: String) : SignalPayload { override val wireType = "answer" }
    data class Candidate(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String) : SignalPayload {
        override val wireType = "candidate"
    }
    /** My microphone / camera is on or off, so the other phone can show it. */
    /** [screen] = the picture I send is my phone screen ("Demonstrate"), not my camera. */
    data class Media(val audio: Boolean, val video: Boolean, val screen: Boolean = false) : SignalPayload { override val wireType = "media" }
    data object Bye : SignalPayload { override val wireType = "bye" }
    /** "I have no connection to you, please send me a fresh offer." */
    data object Renegotiate : SignalPayload { override val wireType = "renegotiate" }
}

/** A message I send to one person. [id] is unique, so a repeated delivery can be ignored. */
data class OutgoingSignal(val id: String, val to: Long, val payload: SignalPayload)

/** Why a room closed. */
enum class EndReason(val wire: String) {
    ENDED_BY_USER("ended_by_user"), EMPTY("empty"), EXPIRED("expired"),
    BOOKING_CANCELLED("booking_cancelled"), BOOKING_DONE("booking_done"), SUPERSEDED("superseded"),
    LEFT("left"), UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): EndReason = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** Everything the room stream can tell the phone. */
sealed interface RoomEvent {
    data class State(
        val roomId: String, val connId: Long, val you: Participant, val participants: List<Participant>,
        val startedAt: Long, val expiresAt: Long
    ) : RoomEvent {
        val peer: Participant? get() = participants.firstOrNull { it.userId != you.userId }
    }
    /** [resumed] = they came back within the grace period (e.g. Wi-Fi -> mobile data). I am the offerer. */
    data class PeerJoined(val participant: Participant, val resumed: Boolean) : RoomEvent
    data class PeerLeft(val userId: Long, val reason: String) : RoomEvent
    data class Signal(val id: String, val from: Long, val payload: SignalPayload) : RoomEvent
    data class Ended(val reason: EndReason, val by: Long?) : RoomEvent
}

object Protocol {

    fun signalJson(s: OutgoingSignal): JSONObject {
        val payload = JSONObject()
        when (val p = s.payload) {
            is SignalPayload.Offer -> { payload.put("sdp", p.sdp); if (p.iceRestart) payload.put("iceRestart", true) }
            is SignalPayload.Answer -> payload.put("sdp", p.sdp)
            is SignalPayload.Candidate -> payload.put("candidate", p.candidate).put("sdpMLineIndex", p.sdpMLineIndex)
                .put("sdpMid", p.sdpMid ?: JSONObject.NULL)
            is SignalPayload.Media -> { payload.put("audio", p.audio).put("video", p.video); if (p.screen) payload.put("screen", true) }
            SignalPayload.Bye, SignalPayload.Renegotiate -> Unit
        }
        return JSONObject().put("id", s.id).put("to", s.to).put("type", s.payload.wireType).put("payload", payload)
    }

    /** Reads a payload from the server. Unknown or broken messages give null (and are ignored). */
    fun parsePayload(type: String, p: JSONObject?): SignalPayload? {
        val o = p ?: JSONObject()
        return when (type) {
            "offer" -> o.optString("sdp").takeIf { it.isNotBlank() }?.let { SignalPayload.Offer(it, o.optBoolean("iceRestart", false)) }
            "answer" -> o.optString("sdp").takeIf { it.isNotBlank() }?.let { SignalPayload.Answer(it) }
            "candidate" -> {
                val c = o.optString("candidate")
                if (c.isBlank() || !o.has("sdpMLineIndex")) null
                else SignalPayload.Candidate(if (o.isNull("sdpMid")) null else o.optString("sdpMid"), o.optInt("sdpMLineIndex"), c)
            }
            "media" -> if (o.has("audio") && o.has("video")) SignalPayload.Media(o.optBoolean("audio"), o.optBoolean("video"), o.optBoolean("screen", false)) else null
            "bye" -> SignalPayload.Bye
            "renegotiate" -> SignalPayload.Renegotiate
            else -> null
        }
    }

    /** One SSE event -> a [RoomEvent]; null for anything unknown (a newer server may send more). */
    fun parseEvent(type: String, data: JSONObject): RoomEvent? = when (type) {
        "room-state" -> RoomEvent.State(
            roomId = data.optString("roomId"),
            connId = data.optLong("connId"),
            you = participant(data.optJSONObject("you") ?: JSONObject()),
            participants = participants(data.optJSONArray("participants")),
            startedAt = data.optLong("startedAt"),
            expiresAt = data.optLong("expiresAt")
        )
        "peer-joined" -> RoomEvent.PeerJoined(participant(data, onlineDefault = true), data.optBoolean("resumed", false))
        "peer-left" -> RoomEvent.PeerLeft(data.optLong("userId"), data.optString("reason", "left"))
        "signal" -> parsePayload(data.optString("type"), data.optJSONObject("payload"))?.let {
            RoomEvent.Signal(data.optString("id"), data.optLong("from"), it)
        }
        "room-ended" -> RoomEvent.Ended(EndReason.fromWire(data.optString("reason")),
            if (data.isNull("by") || !data.has("by")) null else data.optLong("by"))
        else -> null
    }

    fun parseRoomInfo(o: JSONObject): RoomInfo = RoomInfo(
        roomId = o.getString("roomId"),
        bookingId = o.optLong("bookingId"),
        skill = o.optString("skill"),
        you = participant(o.getJSONObject("you")),
        participants = participants(o.optJSONArray("participants")),
        startedAt = o.optLong("startedAt"),
        expiresAt = o.optLong("expiresAt"),
        iceServers = iceServers(o.optJSONArray("iceServers")),
        turn = o.optBoolean("turn", false)
    )

    fun iceServers(a: JSONArray?): List<IceServerConfig> = (0 until (a?.length() ?: 0)).mapNotNull { i ->
        val s = a!!.optJSONObject(i) ?: return@mapNotNull null
        val urls = when (val u = s.opt("urls")) {
            is JSONArray -> (0 until u.length()).map { u.optString(it) }
            is String -> listOf(u)
            else -> emptyList()
        }.filter { it.isNotBlank() }
        if (urls.isEmpty()) null
        else IceServerConfig(urls, s.optString("username").ifBlank { null }, s.optString("credential").ifBlank { null })
    }

    private fun participants(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it)?.let(::participant) }

    private fun participant(o: JSONObject, onlineDefault: Boolean = false) = Participant(
        userId = o.optLong("userId"), name = o.optString("name"), role = o.optString("role"),
        online = o.optBoolean("online", onlineDefault)
    )
}

/** Remembers the ids of the last messages, so one delivered twice is applied once. */
class RecentIds(private val capacity: Int = 256) {
    private val ids = LinkedHashSet<String>()

    /** true the first time an id is seen. */
    fun firstTime(id: String): Boolean {
        if (id.isEmpty()) return true
        if (!ids.add(id)) return false
        if (ids.size > capacity) ids.remove(ids.first())
        return true
    }

    fun clear() = ids.clear()
}

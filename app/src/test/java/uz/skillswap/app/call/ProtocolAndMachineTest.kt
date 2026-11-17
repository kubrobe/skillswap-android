package uz.skillswap.app.call

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The signalling protocol (same as the server's src/rooms.js) and the call's state machine. */
class ProtocolAndMachineTest {

    // ---------------- protocol ----------------

    @Test fun outgoingSignals_matchTheServerFormat() {
        val offer = Protocol.signalJson(OutgoingSignal("m1", 42, SignalPayload.Offer("v=0", iceRestart = true)))
        assertEquals("m1", offer.getString("id"))
        assertEquals(42L, offer.getLong("to"))
        assertEquals("offer", offer.getString("type"))
        assertEquals("v=0", offer.getJSONObject("payload").getString("sdp"))
        assertTrue(offer.getJSONObject("payload").getBoolean("iceRestart"))

        val cand = Protocol.signalJson(OutgoingSignal("m2", 1, SignalPayload.Candidate(null, 0, "candidate:1"))).getJSONObject("payload")
        assertTrue(cand.isNull("sdpMid"))
        assertEquals(0, cand.getInt("sdpMLineIndex"))
        assertEquals("renegotiate", Protocol.signalJson(OutgoingSignal("m3", 1, SignalPayload.Renegotiate)).getString("type"))
    }

    @Test fun everyPayload_survivesARoundTrip() {
        val all = listOf(
            SignalPayload.Offer("v=0 offer"), SignalPayload.Offer("v=0 restart", iceRestart = true), SignalPayload.Answer("v=0 answer"),
            SignalPayload.Candidate("0", 0, "candidate:1 1 udp 1 10.0.0.1 5000 typ host"), SignalPayload.Candidate(null, 1, "candidate:2"),
            SignalPayload.Media(audio = false, video = true), SignalPayload.Media(audio = true, video = true, screen = true),
            SignalPayload.Bye, SignalPayload.Renegotiate
        )
        for (p in all) {
            val json = Protocol.signalJson(OutgoingSignal("x", 7, p))
            assertEquals(p, Protocol.parsePayload(json.getString("type"), json.getJSONObject("payload")))
        }
    }

    @Test fun media_screenFlag_isOnlySentWhenDemonstrating() {
        // older apps and the website ignore unknown fields; "screen" is left out entirely when false
        val plain = Protocol.signalJson(OutgoingSignal("s1", 1, SignalPayload.Media(audio = true, video = true))).getJSONObject("payload")
        assertTrue(!plain.has("screen"))
        val shared = Protocol.signalJson(OutgoingSignal("s2", 1, SignalPayload.Media(audio = true, video = true, screen = true))).getJSONObject("payload")
        assertTrue(shared.getBoolean("screen"))
        assertEquals(SignalPayload.Media(audio = true, video = false), Protocol.parsePayload("media", JSONObject("""{"audio":true,"video":false}""")))
    }

        @Test fun roomEvents_areParsed_andUnknownOnesIgnored() {
        val state = Protocol.parseEvent("room-state", JSONObject("""
            {"roomId":"r1","connId":3,"you":{"userId":2,"name":"Aziza Karimova","role":"learner"},
             "participants":[{"userId":1,"name":"Bobur Rakhimov","role":"teacher","online":true},
                             {"userId":2,"name":"Aziza Karimova","role":"learner","online":true}],
             "startedAt":10,"expiresAt":20}""")) as RoomEvent.State
        assertEquals(2L, state.you.userId)
        assertEquals("Bobur", state.peer?.firstName)
        assertTrue(state.peer!!.online)

        val joined = Protocol.parseEvent("peer-joined", JSONObject("""{"userId":1,"name":"Bobur","role":"teacher","resumed":true}""")) as RoomEvent.PeerJoined
        assertTrue(joined.resumed)
        assertTrue(joined.participant.online)

        val sig = Protocol.parseEvent("signal", JSONObject("""{"id":"a","from":1,"type":"answer","payload":{"sdp":"v=0"}}""")) as RoomEvent.Signal
        assertEquals(SignalPayload.Answer("v=0"), sig.payload)

        val ended = Protocol.parseEvent("room-ended", JSONObject("""{"reason":"booking_cancelled"}""")) as RoomEvent.Ended
        assertEquals(EndReason.BOOKING_CANCELLED, ended.reason)
        assertNull(ended.by)

        assertNull(Protocol.parseEvent("something-new", JSONObject()))                                    // a newer server
        assertNull(Protocol.parseEvent("signal", JSONObject("""{"id":"b","from":1,"type":"offer","payload":{}}""")))   // broken
        assertEquals(EndReason.UNKNOWN, EndReason.fromWire("??"))
    }

    @Test fun roomInfo_readsIceServers_inBothShapes() {
        val info = Protocol.parseRoomInfo(JSONObject("""
            {"roomId":"r1","bookingId":5,"skill":"Guitar","you":{"userId":2,"name":"A","role":"learner"},
             "participants":[{"userId":1,"name":"B","role":"teacher","online":false},{"userId":2,"name":"A","role":"learner"}],
             "startedAt":1,"expiresAt":2,"turn":true,
             "iceServers":[{"urls":["stun:s1","stun:s2"]},{"urls":"turn:t1?transport=udp","username":"u","credential":"c"},{"urls":[]}]}"""))
        assertEquals(1L, info.peer?.userId)
        assertTrue(info.turn)
        assertEquals(2, info.iceServers.size)                     // the empty one is dropped
        assertEquals(listOf("stun:s1", "stun:s2"), info.iceServers[0].urls)
        assertNull(info.iceServers[0].username)
        assertEquals("u", info.iceServers[1].username)
        assertEquals("c", info.iceServers[1].credential)
    }

    @Test fun recentIds_letEachMessageThroughOnce() {
        val ids = RecentIds(capacity = 2)
        assertTrue(ids.firstTime("a"))
        assertFalse(ids.firstTime("a"))
        assertTrue(ids.firstTime("b"))
        assertTrue(ids.firstTime("c"))          // "a" falls out of the window
        assertTrue(ids.firstTime("a"))
    }

    // ---------------- state machine ----------------

    private fun run(vararg inputs: CallInput) = inputs.fold(CallMachine()) { m, i -> m.next(i) }

    @Test fun firstInTheRoom_waits_thenConnects() {
        var m = run(CallInput.Start)
        assertEquals(CallPhase.CREATING, m.phase)
        m = m.next(CallInput.RoomOpened)
        assertEquals(CallPhase.JOINING, m.phase)
        m = m.next(CallInput.Joined(peerOnline = false))
        assertEquals(CallPhase.WAITING, m.phase)
        m = m.next(CallInput.PeerArrived)
        assertEquals(CallPhase.CONNECTING, m.phase)
        m = m.next(CallInput.MediaUp)
        assertEquals(CallPhase.CONNECTED, m.phase)
    }

    @Test fun mediaDrop_isReconnecting_andComesBack() {
        val m = run(CallInput.Start, CallInput.RoomOpened, CallInput.Joined(true), CallInput.MediaUp, CallInput.MediaDown)
        assertEquals(CallPhase.RECONNECTING, m.phase)
        assertEquals(CallPhase.CONNECTED, m.next(CallInput.MediaUp).phase)
    }

    @Test fun signallingDrop_withMediaStillFlowing_staysConnected() {
        val m = run(CallInput.Start, CallInput.RoomOpened, CallInput.Joined(true), CallInput.MediaUp, CallInput.SignalingLost)
        assertEquals(CallPhase.CONNECTED, m.phase)          // what the person feels: the call goes on
        assertFalse(m.signalingUp)
        val bothDown = m.next(CallInput.MediaDown)
        assertEquals(CallPhase.RECONNECTING, bothDown.phase)
        assertEquals(CallPhase.CONNECTED, bothDown.next(CallInput.Joined(true)).next(CallInput.MediaUp).phase)
    }

    @Test fun peerLeaving_goesBackToWaiting_andANewArrivalIsConnectingNotReconnecting() {
        val m = run(CallInput.Start, CallInput.RoomOpened, CallInput.Joined(true), CallInput.MediaUp, CallInput.PeerGone)
        assertEquals(CallPhase.WAITING, m.phase)
        assertEquals(CallPhase.CONNECTING, m.next(CallInput.PeerArrived).phase)
    }

    @Test fun hangUp_endsOnlyThroughEnding() {
        val ending = run(CallInput.Start, CallInput.RoomOpened, CallInput.Joined(true), CallInput.HangUp)
        assertEquals(CallPhase.ENDING, ending.phase)
        assertEquals(CallPhase.ENDING, ending.next(CallInput.MediaUp).phase)          // late events are ignored
        val ended = ending.next(CallInput.Ended(EndReason.LEFT))
        assertEquals(CallPhase.ENDED, ended.phase)
        assertEquals(EndReason.LEFT, ended.endReason)
    }

    @Test fun finishedCalls_ignoreEverythingButANewStart() {
        val failed = run(CallInput.Start, CallInput.Failed("COULD_NOT_CONNECT"))
        assertEquals(CallPhase.FAILED, failed.phase)
        assertEquals("COULD_NOT_CONNECT", failed.error)
        assertEquals(failed, failed.next(CallInput.MediaUp))
        assertEquals(failed, failed.next(CallInput.HangUp))
        val again = failed.next(CallInput.Start)
        assertEquals(CallPhase.CREATING, again.phase)
        assertNull(again.error)

        val ended = run(CallInput.Start, CallInput.RoomOpened, CallInput.Ended(EndReason.BOOKING_CANCELLED))
        assertEquals(CallPhase.ENDED, ended.phase)
        assertEquals(EndReason.BOOKING_CANCELLED, ended.endReason)
        assertFalse(ended.phase.isActive)
    }

    @Test fun startWhileRunning_changesNothing() {
        val m = run(CallInput.Start, CallInput.RoomOpened, CallInput.Joined(true))
        assertEquals(m, m.next(CallInput.Start))
    }
}

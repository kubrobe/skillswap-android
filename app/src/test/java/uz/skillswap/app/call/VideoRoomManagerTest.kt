package uz.skillswap.app.call

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import uz.skillswap.app.data.remote.ApiException

/**
 * The whole call flow of [VideoRoomManager], without a camera, WebRTC or a server: the fakes below record what
 * the manager asks for, and the tests play the server's and the other phone's part. Time is virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoRoomManagerTest {

    private val me = Participant(2, "Aziza Karimova", "learner")
    private val teacher = Participant(1, "Bobur Rakhimov", "teacher")
    private val labels = CallLabels("Video lesson", "Tap to return", "Leave")

    // ---------------- fakes ----------------

    private class FakeSignaling(var openResult: () -> RoomInfo) : SignalingClient {
        val streams = ArrayDeque<Channel<RoomEvent>>()
        var listening: Channel<RoomEvent>? = null
        var listenCount = 0
        val sent = mutableListOf<OutgoingSignal>()
        var leaves = 0
        var ends = 0
        var failStream: Exception? = null

        fun newStream(): Channel<RoomEvent> = Channel<RoomEvent>(Channel.UNLIMITED).also { streams.addLast(it) }

        override suspend fun openRoom(bookingId: Long) = openResult()
        override suspend fun listen(roomId: String, onEvent: suspend (RoomEvent) -> Unit) {
            listenCount++
            failStream?.let { failStream = null; throw it }
            val ch = streams.removeFirstOrNull() ?: newStream().also { streams.removeLast() }
            listening = ch
            for (e in ch) onEvent(e)                      // returns when the test closes the channel (line broke)
        }
        override suspend fun send(roomId: String, signal: OutgoingSignal) { sent += signal }
        override suspend fun leave(roomId: String) { leaves++ }
        override suspend fun end(roomId: String) { ends++ }

        fun push(e: RoomEvent) { listening!!.trySend(e) }
        fun sentTypes() = sent.map { it.payload.wireType }
    }

    private class FakeLink(override val remoteUserId: Long, override val offerer: Boolean, val events: PeerEvents) : PeerLink {
        var connected = false
        val offers = mutableListOf<Boolean>()          // iceRestart flag of each offer
        val acceptedOffers = mutableListOf<String>()
        val acceptedAnswers = mutableListOf<String>()
        val candidates = mutableListOf<SignalPayload.Candidate>()
        var closed = false
        override val isConnected get() = connected
        override suspend fun makeOffer(iceRestart: Boolean) { offers += iceRestart }
        override suspend fun acceptOffer(sdp: String) { acceptedOffers += sdp }
        override suspend fun acceptAnswer(sdp: String) { acceptedAnswers += sdp }
        override fun addRemoteCandidate(c: SignalPayload.Candidate) { candidates += c }
        override fun close() { closed = true }
        fun ice(state: IceState) { connected = state == IceState.CONNECTED; events.onIceState(this, state) }
    }

    private class FakeMedia(override val hasMic: Boolean, override val hasCamera: Boolean) : CallMedia {
        val links = mutableListOf<FakeLink>()
        var released = false
        private var mic = hasMic
        private var cam = hasCamera
        override val micEnabled get() = mic
        override val cameraEnabled get() = cam
        override val frontCamera = true
        override val canSwitchCamera = true
        override val localVideo: VideoTrack? = null
        override val eglContext: EglBase.Context? = null
        override fun setMicEnabled(on: Boolean) { mic = on }
        override fun setCameraEnabled(on: Boolean) { cam = on }
        override fun switchCamera(done: (Boolean) -> Unit) = done(true)
        override fun connectTo(
            remoteUserId: Long, offerer: Boolean, iceServers: List<IceServerConfig>, scope: CoroutineScope,
            send: (SignalPayload) -> Unit, events: PeerEvents
        ): PeerLink = FakeLink(remoteUserId, offerer, events).also { links += it }
        override fun release() { released = true }
        val link get() = links.last()
    }

    private class FakeAudio : AudioRouting {
        override val routes = MutableStateFlow(listOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE))
        override val route = MutableStateFlow(AudioRoute.SPEAKER)
        var running = false
        override fun start() { running = true }
        override fun select(route: AudioRoute) { this.route.value = route }
        override fun stop() { running = false }
    }

    private class FakeHost : CallHost {
        var active = false
        override fun onCallActive(labels: CallLabels, camera: Boolean, microphone: Boolean) { active = true }
        override fun onCallInactive() { active = false }
    }

    private class Rig(
        val manager: VideoRoomManager, val signaling: FakeSignaling, val audio: FakeAudio, val host: FakeHost,
        private val medias: MutableList<FakeMedia>
    ) {
        val media get() = medias.last()
        val state get() = manager.state.value
    }

    private fun TestScope.rig(peerOnline: Boolean = false, turn: Boolean = true): Rig {
        val info = RoomInfo("room-1", 5, "Guitar", me, listOf(teacher.copy(online = peerOnline), me), 0, 0,
            listOf(IceServerConfig(listOf("stun:s"))), turn)
        val signaling = FakeSignaling { info }
        val medias = mutableListOf<FakeMedia>()
        val audio = FakeAudio()
        val host = FakeHost()
        val manager = VideoRoomManager(
            signaling = signaling,
            mediaFactory = { a, v -> FakeMedia(a, v).also { medias += it } },
            audio = audio, host = host, scope = backgroundScope,
            clock = { testScheduler.currentTime }
        )
        return Rig(manager, signaling, audio, host, medias)
    }

    private fun state(peerOnline: Boolean) =
        RoomEvent.State("room-1", 1, me, listOf(teacher.copy(online = peerOnline), me.copy(online = true)), 0, 0)

    private var n = 0
    private fun signal(p: SignalPayload, id: String = "s${++n}") = RoomEvent.Signal(id, teacher.userId, p)

    /** Join, open the stream and get room-state. */
    private fun TestScope.joined(peerOnline: Boolean, turn: Boolean = true): Rig {
        val r = rig(peerOnline, turn)
        r.signaling.newStream()
        r.manager.join(5, audio = true, video = true, labels)
        runCurrent()
        r.signaling.push(state(peerOnline))
        runCurrent()
        return r
    }

    // ---------------- tests ----------------

    @Test fun newcomer_answersTheOffer_andConnects() = runTest {
        val r = joined(peerOnline = true)
        assertEquals(CallPhase.CONNECTING, r.state.phase)
        assertTrue(r.audio.running)
        assertTrue(r.host.active)

        r.signaling.push(signal(SignalPayload.Candidate("0", 0, "early")))    // before the offer: no connection yet
        r.signaling.push(signal(SignalPayload.Offer("v=0 offer")))
        runCurrent()
        val link = r.media.link
        assertFalse(link.offerer)
        assertEquals(listOf("v=0 offer"), link.acceptedOffers)

        r.signaling.push(signal(SignalPayload.Candidate("0", 0, "c1")))
        runCurrent()
        assertEquals(listOf("c1"), link.candidates.map { it.candidate })

        link.ice(IceState.CONNECTED)
        runCurrent()
        assertEquals(CallPhase.CONNECTED, r.state.phase)
        assertTrue(r.state.connectedSince >= 0L)
        assertTrue("my mic/camera state goes to the other phone", "media" in r.signaling.sentTypes())
        assertTrue(r.signaling.sent.all { it.to == teacher.userId })
    }

    @Test fun firstInTheRoom_waits_thenOffersToTheNewcomer() = runTest {
        val r = joined(peerOnline = false)
        assertEquals(CallPhase.WAITING, r.state.phase)
        assertTrue(r.media.links.isEmpty())

        r.signaling.push(RoomEvent.PeerJoined(teacher.copy(online = true), resumed = false))
        runCurrent()
        assertEquals(CallPhase.CONNECTING, r.state.phase)
        val link = r.media.link
        assertTrue(link.offerer)
        assertEquals(listOf(false), link.offers)

        r.signaling.push(signal(SignalPayload.Answer("v=0 answer")))
        runCurrent()
        assertEquals(listOf("v=0 answer"), link.acceptedAnswers)
        link.ice(IceState.CONNECTED)
        runCurrent()
        assertEquals(CallPhase.CONNECTED, r.state.phase)
    }

    @Test fun aMessageDeliveredTwice_isAppliedOnce() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0"), id = "same"))
        r.signaling.push(signal(SignalPayload.Offer("v=0"), id = "same"))
        runCurrent()
        assertEquals(1, r.media.links.size)
        assertEquals(1, r.media.link.acceptedOffers.size)
    }

    @Test fun messagesFromSomeoneElse_areIgnored() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(RoomEvent.Signal("x", 99, SignalPayload.Offer("v=0 stranger")))
        runCurrent()
        assertTrue(r.media.links.isEmpty())
    }

    @Test fun noOfferInTime_asksForOne() = runTest {
        val r = joined(peerOnline = true)
        assertFalse("renegotiate" in r.signaling.sentTypes())
        advanceTimeBy(4_100)
        runCurrent()
        assertTrue("renegotiate" in r.signaling.sentTypes())
    }

    @Test fun renegotiate_fromThePeer_makesMeOfferOnAFreshConnection() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        val old = r.media.link
        r.signaling.push(signal(SignalPayload.Renegotiate))
        runCurrent()
        assertTrue(old.closed)
        assertNotSame(old, r.media.link)
        assertTrue(r.media.link.offerer)
        assertEquals(listOf(false), r.media.link.offers)
    }

    @Test fun peerLeaving_closesTheConnection_andWaits() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        r.media.link.ice(IceState.CONNECTED)
        runCurrent()
        r.signaling.push(RoomEvent.PeerLeft(teacher.userId, "left"))
        runCurrent()
        assertTrue(r.media.link.closed)
        assertEquals(CallPhase.WAITING, r.state.phase)
        assertEquals("left", r.state.peerLeftReason)
        assertFalse(r.media.released)                      // I stay in the room with my camera on
    }

    @Test fun mediaDrop_offererRestartsIce_thenGivesUpAfterTheTimeout() = runTest {
        val r = joined(peerOnline = false)
        r.signaling.push(RoomEvent.PeerJoined(teacher.copy(online = true), resumed = false))
        runCurrent()
        val link = r.media.link
        link.ice(IceState.CONNECTED)
        runCurrent()

        link.ice(IceState.DISCONNECTED)
        runCurrent()
        assertEquals(CallPhase.RECONNECTING, r.state.phase)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals("an ICE restart on the same connection", listOf(false, true), link.offers)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(CallPhase.FAILED, r.state.phase)
        assertEquals(CallError.CONNECTION_LOST, r.state.error)
        assertTrue(link.closed)
        assertTrue(r.media.released)
        assertFalse(r.audio.running)
        assertFalse(r.host.active)
        assertEquals(1, r.signaling.leaves)                // the other person is told at once
    }

    @Test fun answererAcceptsAnIceRestart_onTheSameConnection() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        val link = r.media.link
        r.signaling.push(signal(SignalPayload.Offer("v=0 restart", iceRestart = true)))
        runCurrent()
        assertSame(link, r.media.link)
        assertEquals(listOf("v=0", "v=0 restart"), link.acceptedOffers)
    }

    @Test fun neverConnecting_failsWithAClearError() = runTest {
        val r = joined(peerOnline = true, turn = false)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        advanceTimeBy(36_000)
        runCurrent()
        assertEquals(CallPhase.FAILED, r.state.phase)
        assertEquals(CallError.COULD_NOT_CONNECT, r.state.error)
        assertFalse(r.state.turn)
    }

    @Test fun roomStreamDrop_reconnects_andKeepsAWorkingConnection() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        val link = r.media.link
        link.ice(IceState.CONNECTED)
        runCurrent()

        val next = r.signaling.newStream()
        r.signaling.listening!!.close()                    // the line broke (e.g. Wi-Fi -> 4G)
        runCurrent()
        assertEquals("media still flows", CallPhase.CONNECTED, r.state.phase)
        assertFalse(r.state.signalingUp)
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(2, r.signaling.listenCount)
        assertSame(next, r.signaling.listening)
        r.signaling.push(state(peerOnline = true))
        runCurrent()
        assertTrue(r.state.signalingUp)
        assertSame("no new connection, no video blink", link, r.media.link)
        assertEquals(CallPhase.CONNECTED, r.state.phase)
    }

    @Test fun peerComingBackWithinGrace_withAWorkingConnection_isNotReoffered() = runTest {
        val r = joined(peerOnline = false)
        r.signaling.push(RoomEvent.PeerJoined(teacher.copy(online = true), resumed = false))
        runCurrent()
        val link = r.media.link
        link.ice(IceState.CONNECTED)
        runCurrent()
        r.signaling.push(RoomEvent.PeerJoined(teacher.copy(online = true), resumed = true))
        runCurrent()
        assertSame(link, r.media.link)
        assertEquals(listOf(false), link.offers)
        // ...but a real re-join (their app restarted) gets a fresh offer
        r.signaling.push(RoomEvent.PeerJoined(teacher.copy(online = true), resumed = false))
        runCurrent()
        assertTrue(link.closed)
        assertEquals(listOf(false), r.media.link.offers)
    }

    @Test fun twoOffersAtOnce_theSmallerUserIdKeepsOffering() = runTest {
        // I (id 2) offered first, then the teacher (id 1) offers too: 1 < 2, so I answer theirs
        val r = joined(peerOnline = false)
        r.signaling.push(RoomEvent.PeerJoined(teacher.copy(online = true), resumed = false))
        runCurrent()
        val mine = r.media.link
        r.signaling.push(signal(SignalPayload.Offer("v=0 theirs")))
        runCurrent()
        assertTrue(mine.closed)
        assertFalse(r.media.link.offerer)
        assertEquals(listOf("v=0 theirs"), r.media.link.acceptedOffers)
    }

    @Test fun hangUp_releasesEverything_andTellsTheServer() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        r.manager.hangUp()
        assertTrue("camera and mic off at once", r.media.released)
        runCurrent()
        assertEquals(CallPhase.ENDED, r.state.phase)
        assertEquals(EndReason.LEFT, r.state.endReason)
        assertTrue("bye" in r.signaling.sentTypes())
        assertEquals(1, r.signaling.leaves)
        assertEquals(0, r.signaling.ends)
        assertFalse(r.audio.running)
        assertFalse(r.host.active)
        assertNull(r.state.localVideo)
    }

    @Test fun endForBoth_usesEnd() = runTest {
        val r = joined(peerOnline = true)
        r.manager.hangUp(forBoth = true)
        runCurrent()
        assertEquals(1, r.signaling.ends)
        assertEquals(EndReason.ENDED_BY_USER, r.state.endReason)
    }

    @Test fun roomClosedByTheServer_endsTheCallWithTheReason() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(RoomEvent.Ended(EndReason.BOOKING_CANCELLED, null))
        runCurrent()
        assertEquals(CallPhase.ENDED, r.state.phase)
        assertEquals(EndReason.BOOKING_CANCELLED, r.state.endReason)
        assertTrue(r.media.released)
        assertEquals(0, r.signaling.leaves)
    }

    @Test fun roomGoneWhileReconnecting_410_endsWithTheServersMessage() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.failStream = ApiException(410, "This lesson was cancelled, so its video room is closed.")
        r.signaling.listening!!.close()
        runCurrent()
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(CallPhase.ENDED, r.state.phase)
        assertEquals("This lesson was cancelled, so its video room is closed.", r.state.errorDetail)
    }

    @Test fun openRoomRefused_failsWithTheServersMessage_andReleasesTheCamera() = runTest {
        val r = rig()
        r.signaling.openResult = { throw ApiException(409, "This lesson was cancelled, so its video room is closed.") }
        r.manager.join(5, audio = true, video = true, labels)
        runCurrent()
        assertEquals(CallPhase.FAILED, r.state.phase)
        assertEquals(CallError.SERVER, r.state.error)
        assertNotNull(r.state.errorDetail)
        assertTrue(r.media.released)
        assertFalse(r.host.active)
    }

    @Test fun micAndCamera_toggle_andTheOtherPhoneIsTold() = runTest {
        val r = joined(peerOnline = true)
        r.signaling.push(signal(SignalPayload.Offer("v=0")))
        runCurrent()
        r.manager.toggleMic()
        r.manager.toggleCamera()
        runCurrent()
        assertFalse(r.state.micOn)
        assertFalse(r.state.cameraOn)
        val last = r.signaling.sent.last { it.payload is SignalPayload.Media }.payload as SignalPayload.Media
        assertEquals(SignalPayload.Media(audio = false, video = false), last)

        r.signaling.push(signal(SignalPayload.Media(audio = false, video = true)))
        runCurrent()
        assertFalse(r.state.peerMicOn)
        assertTrue(r.state.peerCameraOn)
    }

    @Test fun background_pausesTheCamera_andForegroundBringsItBack() = runTest {
        val r = joined(peerOnline = true)
        r.manager.onAppBackground()
        assertFalse(r.media.cameraEnabled)
        assertTrue(r.state.cameraPaused)
        r.manager.toggleCamera()                          // cannot turn it on while in the background
        assertFalse(r.media.cameraEnabled)
        r.manager.onAppForeground()
        assertTrue(r.media.cameraEnabled)
        assertFalse(r.state.cameraPaused)
    }

    @Test fun withoutMicPermission_theCallStillWorks_videoOnly() = runTest {
        val r = rig(peerOnline = true)
        r.signaling.newStream()
        r.manager.join(5, audio = false, video = true, labels)
        runCurrent()
        assertFalse(r.state.hasMic)
        assertTrue(r.state.hasCamera)
        r.manager.toggleMic()                              // nothing to toggle
        assertFalse(r.state.micOn)
    }

    @Test fun oneCallAtATime_andRetryStartsTheSameLessonAgain() = runTest {
        val r = joined(peerOnline = true)
        r.manager.join(6, audio = true, video = true, labels)
        assertEquals(CallError.BUSY, r.state.error)
        assertEquals(5L, r.state.bookingId)
        r.manager.clearBusyError()

        r.signaling.push(RoomEvent.Ended(EndReason.EXPIRED, null))
        runCurrent()
        r.signaling.newStream()
        r.manager.retry()
        runCurrent()
        assertTrue(r.state.phase.isActive)
        assertEquals(5L, r.state.bookingId)
        assertEquals(2, r.signaling.listenCount)
    }

    @Test fun audioRoute_isShownAndCanBeChanged() = runTest {
        val r = joined(peerOnline = true)
        assertEquals(listOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE), r.state.audioRoutes)
        r.manager.selectAudioRoute(AudioRoute.EARPIECE)
        runCurrent()
        assertEquals(AudioRoute.EARPIECE, r.state.audioRoute)
    }
}

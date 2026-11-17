package uz.skillswap.app.call

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import uz.skillswap.app.data.remote.ApiException
import uz.skillswap.app.data.remote.OfflineException
import java.util.UUID

/** Why a call could not go on. The screen turns it into text in the app's language. */
enum class CallError { CAMERA_MIC, OFFLINE, SERVER, COULD_NOT_CONNECT, CONNECTION_LOST, SESSION_EXPIRED, BUSY }

/** Everything the call screen shows. */
data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val bookingId: Long = 0L,
    val roomId: String? = null,
    val skill: String = "",
    val me: Participant? = null,
    val peer: Participant? = null,
    val peerMicOn: Boolean = true,
    val peerCameraOn: Boolean = true,
    val peerSharingScreen: Boolean = false, // the other person is demonstrating their screen
    val sharingScreen: Boolean = false,     // I am demonstrating my screen
    val peerLeftReason: String? = null,     // "left" / "disconnected": the other person went away during the call
    val hasMic: Boolean = false,
    val hasCamera: Boolean = false,
    val micOn: Boolean = false,
    val cameraOn: Boolean = false,
    val cameraPaused: Boolean = false,      // the app is in the background: camera off, sound goes on
    val frontCamera: Boolean = true,
    val canSwitchCamera: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.SPEAKER,
    val audioRoutes: List<AudioRoute> = emptyList(),
    val connectedSince: Long = 0L,          // when the media first flowed (for the timer); 0 = not yet
    val turn: Boolean = true,               // false = the server has no TURN relay: some networks will not connect
    val signalingUp: Boolean = false,
    val endReason: EndReason? = null,
    val error: CallError? = null,
    val errorDetail: String? = null,        // the server's own message, e.g. "This lesson was cancelled..."
    val localVideo: VideoTrack? = null,
    val remoteVideo: VideoTrack? = null,
    val eglContext: EglBase.Context? = null
)

/**
 * Runs ONE video lesson call at a time, for the whole app (it lives in SkillSwapApp, not in a screen, so turning
 * the phone, leaving the screen or the app going to the background never drops the call).
 *
 * It connects four things and keeps them in step:
 *   [signaling] (our server: room + connection messages), [CallMedia] (camera, microphone, WebRTC),
 *   [AudioRouting] (speaker...) and [CallHost] (ongoing notification), with [CallMachine] deciding the phase.
 *
 * Reliability rules (see also the server's src/rooms.js):
 * - The person already in the room offers; the newcomer answers. If no offer comes within [CallTiming.offerWaitMs]
 *   the newcomer asks for one ("renegotiate"). Two offers at once (rare): the smaller user id keeps offering.
 * - ICE candidates that arrive before the offer/answer are kept by the PeerLink until it can use them.
 * - Every message has an id; one delivered twice is ignored.
 * - The room stream reconnects (1, 2, 4, 8 s...); media keeps flowing meanwhile. If the media path breaks, the
 *   offerer makes an ICE restart (new network path, same call), up to [CallTiming.maxIceRestarts] times.
 * - Stuck connecting / reconnecting for too long -> FAILED with "Try again". Everything is released on every end.
 *
 * All functions must be called on the main thread ([scope] runs on it in the app).
 */
class VideoRoomManager(
    private val signaling: SignalingClient,
    private val mediaFactory: (audio: Boolean, video: Boolean) -> CallMedia,
    private val audio: AudioRouting,
    private val host: CallHost,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
    private val timing: CallTiming = CallTiming()
) : PeerEvents {

    data class CallTiming(
        val connectTimeoutMs: Long = 35_000,
        val reconnectTimeoutMs: Long = 30_000,
        val iceRestartDelayMs: Long = 3_000,
        val offerWaitMs: Long = 4_000,
        val maxIceRestarts: Int = 3,
        val streamRetryMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000)
    )

    private class JoinRequest(val bookingId: Long, val audio: Boolean, val video: Boolean, val labels: CallLabels)

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val mainContext = scope.coroutineContext.minusKey(Job)
    private var machine = CallMachine()
    private var phaseSince = 0L
    private var request: JoinRequest? = null
    private var room: RoomInfo? = null
    private var media: CallMedia? = null
    private var link: PeerLink? = null
    private var callJob: Job? = null
    private var outbox: Channel<OutgoingSignal>? = null
    private var offerWaitJob: Job? = null
    private var iceRestartJob: Job? = null
    private var iceRestarts = 0
    private var pausedForBackground = false
    private val seen = RecentIds()

    private val me: Long? get() = _state.value.me?.userId
    private val peerId: Long? get() = _state.value.peer?.userId

    // ---------------- what the screen calls ----------------

    /** Start (or return to) the video lesson of [bookingId]. [audio]/[video] = permission granted for mic/camera. */
    fun join(bookingId: Long, audio: Boolean, video: Boolean, labels: CallLabels) {
        val now = _state.value
        if (now.phase.isActive || now.phase == CallPhase.ENDING) {
            if (now.bookingId != bookingId) _state.update { it.copy(error = CallError.BUSY) }   // one call at a time
            return
        }
        request = JoinRequest(bookingId, audio, video, labels)
        machine = CallMachine()
        seen.clear()
        iceRestarts = 0
        pausedForBackground = false
        _state.value = CallUiState(bookingId = bookingId)
        input(CallInput.Start)
        log("call start booking=$bookingId audio=$audio video=$video")
        callJob = scope.launch { runCall(bookingId, audio, video, labels) }
    }

    fun toggleMic() {
        val m = media ?: return
        if (!m.hasMic) return
        m.setMicEnabled(!m.micEnabled)
        refreshLocal()
        sendMediaState()
    }

    fun toggleCamera() {
        val m = media ?: return
        if (!m.hasCamera || pausedForBackground || m.sharingScreen) return
        m.setCameraEnabled(!m.cameraEnabled)
        refreshLocal()
        sendMediaState()
    }

    /**
     * "Demonstrate": send my screen instead of my camera, with the permission Android's dialog returned.
     * The same connection keeps going (only the picture changes), so nothing can drop the call here.
     */
    fun startScreenShare(permission: Intent) {
        val m = media ?: return
        if (!machine.phase.isActive || m.sharingScreen || !m.hasCamera) return
        host.onScreenShare(true) { ok ->
            scope.launch {
                if (media !== m || !machine.phase.isActive || m.sharingScreen) return@launch
                val started = ok && m.startScreenShare(permission, onStopped = { stopScreenShare() })
                if (!started) {
                    log("screen share could not start")
                    host.onScreenShare(false)
                    return@launch
                }
                log("screen share on")
                link?.setOutgoingVideo(m.outgoingVideo)
                refreshLocal()
                sendMediaState()
            }
        }
    }

    /** Back to the camera (my button, or Android's "Stop sharing"). */
    fun stopScreenShare() {
        val m = media ?: return
        if (!m.sharingScreen) return
        m.stopScreenShare { link?.setOutgoingVideo(m.outgoingVideo) }
        host.onScreenShare(false)
        log("screen share off")
        refreshLocal()
        sendMediaState()
    }

    fun switchCamera() {
        val m = media ?: return
        if (!m.cameraEnabled) return
        m.switchCamera { scope.launch { refreshLocal() } }
    }

    fun selectAudioRoute(route: AudioRoute) = audio.select(route)

    /** The red button. [forBoth] = end the room for the other person too (otherwise they may wait for me). */
    fun hangUp(forBoth: Boolean = false) {
        if (!machine.phase.isActive) return
        val roomId = room?.roomId
        val to = peerId
        val hadPeer = link != null
        input(CallInput.HangUp)
        log("hang up forBoth=$forBoth")
        teardown()                               // camera and microphone off at once
        scope.launch {
            if (roomId != null) {
                withTimeoutOrNull(5_000) {
                    if (to != null && hadPeer) runCatching { signaling.send(roomId, OutgoingSignal(newId(), to, SignalPayload.Bye)) }
                    runCatching { if (forBoth) signaling.end(roomId) else signaling.leave(roomId) }
                        .onFailure { log("leave failed: ${it.javaClass.simpleName}") }   // the server's grace timer covers it
                }
            }
            input(CallInput.Ended(if (forBoth) EndReason.ENDED_BY_USER else EndReason.LEFT))
        }
    }

    /** The app was swiped away from the recent apps: leave politely while we still can. */
    fun onAppClosed() = hangUp(forBoth = false)

    /** The app went to the background: the camera stops (privacy, battery), the sound goes on. */
    fun onAppBackground() {
        val m = media ?: return
        if (!machine.phase.isActive || !m.cameraEnabled) return
        m.setCameraEnabled(false)
        pausedForBackground = true
        refreshLocal()
        sendMediaState()
    }

    fun onAppForeground() {
        val m = media ?: return
        if (!pausedForBackground) return
        pausedForBackground = false
        if (machine.phase.isActive) m.setCameraEnabled(true)
        refreshLocal()
        sendMediaState()
    }

    /** After ENDED / FAILED: back to IDLE, so the next lesson starts clean. */
    fun dismiss() {
        if (machine.phase.isActive || machine.phase == CallPhase.ENDING) return
        machine = CallMachine()
        _state.value = CallUiState()
    }

    /** "Try again" after FAILED (or ENDED): the same lesson, the same permissions. */
    fun retry() {
        val r = request ?: return
        if (machine.phase.isActive) return
        dismiss()
        join(r.bookingId, r.audio, r.video, r.labels)
    }

    fun clearBusyError() = _state.update { if (it.error == CallError.BUSY) it.copy(error = null) else it }

    // ---------------- the call ----------------

    private suspend fun runCall(bookingId: Long, wantAudio: Boolean, wantVideo: Boolean, labels: CallLabels) {
        val m = try {
            mediaFactory(wantAudio, wantVideo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("media start failed: ${e.javaClass.simpleName}")
            return fail(CallError.CAMERA_MIC)
        }
        media = m
        audio.start()
        host.onCallActive(labels, camera = m.hasCamera, microphone = m.hasMic)
        refreshLocal()

        val info = try {
            signaling.openRoom(bookingId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failFrom(e)
        }
        room = info
        _state.update { it.copy(roomId = info.roomId, skill = info.skill, me = info.you, peer = info.peer, turn = info.turn) }
        input(CallInput.RoomOpened)
        log("room open turn=${info.turn}")

        val box = Channel<OutgoingSignal>(Channel.UNLIMITED)
        outbox = box
        coroutineScope {
            launch { sendLoop(info.roomId, box) }
            launch { watchdog() }
            launch { combine(audio.routes, audio.route) { list, r -> list to r }.collect { (list, r) ->
                _state.update { it.copy(audioRoutes = list, audioRoute = r) }
            } }
            streamLoop(info.roomId)
        }
    }

    private suspend fun streamLoop(roomId: String) {
        var attempt = 0
        while (machine.phase.isActive && currentCoroutineContext().isActive) {
            try {
                signaling.listen(roomId) { event ->
                    withContext(mainContext) {
                        attempt = 0
                        handle(event)
                    }
                }
                log("room stream closed by the server")
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 410) return endedByServer(EndReason.UNKNOWN, e.message)
                if (e.status == 404) return fail(CallError.SERVER, e.message)
                log("room stream error ${e.status}")
            } catch (e: IllegalStateException) {
                return fail(CallError.SESSION_EXPIRED, e.message)
            } catch (e: Exception) {
                log("room stream lost: ${e.javaClass.simpleName}")
            }
            if (!machine.phase.isActive) return
            input(CallInput.SignalingLost)
            delay(timing.streamRetryMs[attempt.coerceAtMost(timing.streamRetryMs.lastIndex)])
            attempt++
        }
    }

    /** Messages go out one at a time, in order (an offer must reach the other phone before its candidates). */
    private suspend fun sendLoop(roomId: String, box: Channel<OutgoingSignal>) {
        for (msg in box) {
            var tries = 0
            while (true) {
                try {
                    signaling.send(roomId, msg)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException) {
                    // 409 = the other person is not connected right now; they will get a fresh offer when back
                    if (e.status != 409) log("signal ${msg.payload.wireType} refused: ${e.status}")
                    break
                } catch (e: OfflineException) {
                    if (++tries > 3) { log("signal ${msg.payload.wireType} dropped (offline)"); break }
                    delay(700L * tries)
                } catch (e: Exception) {
                    log("signal ${msg.payload.wireType} failed: ${e.javaClass.simpleName}")
                    break
                }
            }
        }
    }

    /** Gives up when a call is stuck connecting or reconnecting. */
    private suspend fun watchdog() {
        while (true) {
            delay(1_000)
            val waited = clock() - phaseSince
            when (machine.phase) {
                CallPhase.CONNECTING -> if (waited > timing.connectTimeoutMs) return fail(CallError.COULD_NOT_CONNECT)
                CallPhase.RECONNECTING -> if (waited > timing.reconnectTimeoutMs) return fail(CallError.CONNECTION_LOST)
                else -> Unit
            }
        }
    }

    private suspend fun handle(event: RoomEvent) {
        if (!machine.phase.isActive) return
        when (event) {
            is RoomEvent.State -> {
                log("joined room, peer online=${event.peer?.online}")
                _state.update { it.copy(me = event.you, peer = event.peer ?: it.peer) }
                val peerOnline = event.peer?.online == true
                if (!peerOnline && link != null) closeLink()           // they left while my stream was down
                input(CallInput.Joined(peerOnline))
                val l = link
                when {
                    !peerOnline -> Unit
                    l == null || (!l.offerer && !l.isConnected) -> scheduleOfferWait()   // I am the newcomer: wait for their offer
                    l.offerer && !l.isConnected -> restartIce()                          // back online: find a new path now
                    else -> input(CallInput.MediaUp)
                }
            }
            is RoomEvent.PeerJoined -> {
                if (event.participant.userId == me) return
                log("peer joined resumed=${event.resumed}")
                _state.update { it.copy(peer = event.participant.copy(online = true), peerLeftReason = null) }
                input(CallInput.PeerArrived)
                val l = link
                if (event.resumed && l != null && l.isConnected) {      // only their signalling blinked
                    input(CallInput.MediaUp)
                    sendMediaState()
                    return
                }
                startOffer()
            }
            is RoomEvent.PeerLeft -> {
                if (event.userId != peerId) return
                log("peer left reason=${event.reason}")
                closeLink()
                _state.update { it.copy(peer = it.peer?.copy(online = false), peerLeftReason = event.reason, remoteVideo = null, peerSharingScreen = false) }
                input(CallInput.PeerGone)
            }
            is RoomEvent.Signal -> {
                if (!seen.firstTime(event.id)) return
                if (event.from == me) return
                if (peerId == null) _state.update { it.copy(peer = Participant(event.from, "", "", online = true)) }
                if (event.from != peerId) return
                onSignal(event.payload)
            }
            is RoomEvent.Ended -> {
                log("room ended reason=${event.reason}")
                endedByServer(event.reason, null)
            }
        }
    }

    private suspend fun onSignal(p: SignalPayload) {
        when (p) {
            is SignalPayload.Offer -> {
                offerWaitJob?.cancel()
                val l = link
                if (p.iceRestart && l != null && !l.offerer) {
                    l.acceptOffer(p.sdp)                                 // same connection, new network path
                    return
                }
                if (l != null && l.offerer && !l.isConnected) {
                    // both offered at once: the smaller user id keeps offering, the other one answers
                    val mine = me ?: 0L
                    val theirs = peerId ?: 0L
                    if (mine < theirs) { log("offer collision: keeping my offer"); return }
                    log("offer collision: answering theirs")
                }
                closeLink()
                input(CallInput.PeerArrived)
                val fresh = newLink(offerer = false) ?: return
                fresh.acceptOffer(p.sdp)
                sendMediaState()
            }
            is SignalPayload.Answer -> link?.takeIf { it.offerer }?.acceptAnswer(p.sdp)
            is SignalPayload.Candidate -> link?.addRemoteCandidate(p)
            is SignalPayload.Media -> _state.update { it.copy(peerMicOn = p.audio, peerCameraOn = p.video, peerSharingScreen = p.screen) }
            SignalPayload.Bye -> {
                closeLink()
                _state.update { it.copy(peerLeftReason = "left", remoteVideo = null, peerSharingScreen = false) }
                input(CallInput.PeerGone)
            }
            SignalPayload.Renegotiate -> {
                log("peer asked for a fresh offer")
                startOffer()
            }
        }
    }

    private fun startOffer() {
        closeLink()
        val l = newLink(offerer = true) ?: return
        scope.launch {
            l.makeOffer(iceRestart = false)
            sendMediaState()
        }
    }

    private fun newLink(offerer: Boolean): PeerLink? {
        val m = media ?: return null
        val r = room ?: return null
        val to = peerId ?: return null
        iceRestarts = 0
        return try {
            m.connectTo(to, offerer, r.iceServers, scope, send = { enqueue(it) }, events = this).also { link = it }
        } catch (e: Exception) {
            log("connection create failed: ${e.javaClass.simpleName}")
            fail(CallError.COULD_NOT_CONNECT)
            null
        }
    }

    private fun closeLink() {
        iceRestartJob?.cancel()
        link?.close()
        link = null
        _state.update { it.copy(remoteVideo = null) }
        if (machine.mediaUp) input(CallInput.MediaDown)
    }

    private fun scheduleOfferWait() {
        offerWaitJob?.cancel()
        offerWaitJob = scope.launch {
            delay(timing.offerWaitMs)
            val l = link
            if (_state.value.peer?.online == true && (l == null || (!l.offerer && !l.isConnected))) {
                log("no offer yet: asking for one")
                enqueue(SignalPayload.Renegotiate)
            }
        }
    }

    private fun restartIce() {
        val l = link ?: return
        if (!l.offerer) return
        if (iceRestarts >= timing.maxIceRestarts) return fail(CallError.CONNECTION_LOST)
        iceRestarts++
        log("ice restart #$iceRestarts")
        scope.launch { l.makeOffer(iceRestart = true) }
    }

    // ---------------- WebRTC events (PeerEvents) ----------------

    override fun onRemoteVideo(link: PeerLink, track: VideoTrack?) {
        if (link !== this.link) return
        _state.update { it.copy(remoteVideo = track) }
    }

    override fun onIceState(link: PeerLink, state: IceState) {
        if (link !== this.link || !machine.phase.isActive) return
        when (state) {
            IceState.CONNECTED -> {
                iceRestartJob?.cancel()
                iceRestarts = 0
                input(CallInput.MediaUp)
                if (_state.value.connectedSince == 0L) _state.update { it.copy(connectedSince = clock()) }
            }
            IceState.DISCONNECTED -> {
                input(CallInput.MediaDown)
                // often comes back by itself within a few seconds (a short network blink); restart if it does not
                if (link.offerer) {
                    iceRestartJob?.cancel()
                    iceRestartJob = scope.launch {
                        delay(timing.iceRestartDelayMs)
                        if (this@VideoRoomManager.link === link && !link.isConnected) restartIce()
                    }
                }
            }
            IceState.FAILED -> {
                input(CallInput.MediaDown)
                if (link.offerer) restartIce()
            }
            IceState.NEW, IceState.CHECKING, IceState.CLOSED -> Unit
        }
    }

    override fun onNegotiationError(link: PeerLink, message: String) {
        if (link !== this.link) return
        log("negotiation error: $message")
        if (link.offerer) startOffer() else enqueue(SignalPayload.Renegotiate)
    }

    // ---------------- helpers ----------------

    private fun enqueue(payload: SignalPayload) {
        val to = peerId ?: return
        outbox?.trySend(OutgoingSignal(newId(), to, payload))
    }

    private fun sendMediaState() {
        val m = media ?: return
        if (link == null) return
        enqueue(SignalPayload.Media(audio = m.hasMic && m.micEnabled, video = m.cameraEnabled || m.sharingScreen, screen = m.sharingScreen))
    }

    private fun refreshLocal() {
        val m = media ?: return
        _state.update {
            it.copy(
                hasMic = m.hasMic, hasCamera = m.hasCamera, micOn = m.hasMic && m.micEnabled,
                cameraOn = m.cameraEnabled, cameraPaused = pausedForBackground, frontCamera = m.frontCamera,
                canSwitchCamera = m.canSwitchCamera, localVideo = m.localVideo, eglContext = m.eglContext,
                sharingScreen = m.sharingScreen
            )
        }
    }

    private fun input(i: CallInput) {
        val before = machine.phase
        machine = machine.next(i)
        if (machine.phase != before) {
            phaseSince = clock()
            log("phase $before -> ${machine.phase}")
        }
        _state.update { it.copy(phase = machine.phase, signalingUp = machine.signalingUp, endReason = machine.endReason) }
    }

    private fun failFrom(e: Exception) = when (e) {
        is OfflineException -> fail(CallError.OFFLINE, e.message)
        is ApiException -> fail(CallError.SERVER, e.message)
        is IllegalStateException -> fail(CallError.SESSION_EXPIRED, e.message)
        else -> fail(CallError.SERVER, e.message)
    }

    private fun fail(error: CallError, detail: String? = null) {
        if (!machine.phase.isActive) return
        log("call failed: $error")
        input(CallInput.Failed(error.name))
        _state.update { it.copy(error = error, errorDetail = detail) }
        val roomId = room?.roomId
        teardown()
        // tell the server I am gone (best effort), so the other person is not left waiting for the grace timer
        if (roomId != null) scope.launch { withTimeoutOrNull(5_000) { runCatching { signaling.leave(roomId) } } }
    }

    private fun endedByServer(reason: EndReason, detail: String?) {
        if (!machine.phase.isActive) return
        input(CallInput.Ended(reason))
        _state.update { it.copy(errorDetail = detail) }
        teardown()
    }

    /** Frees everything a call holds. Safe to call more than once. */
    private fun teardown() {
        offerWaitJob?.cancel()
        iceRestartJob?.cancel()
        outbox?.close()
        outbox = null
        link?.close()
        link = null
        media?.release()
        media = null
        room = null
        audio.stop()
        host.onCallInactive()
        _state.update {
            it.copy(localVideo = null, remoteVideo = null, eglContext = null, micOn = false, cameraOn = false, sharingScreen = false)
        }
        val job = callJob
        callJob = null
        job?.cancel()                     // last: this may be the job we are running in
    }

    private fun newId() = UUID.randomUUID().toString()
}

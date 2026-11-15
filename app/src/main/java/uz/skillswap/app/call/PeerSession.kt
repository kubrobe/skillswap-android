package uz.skillswap.app.call

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The WebRTC connection to ONE other person (a group lesson would have one per person, or an SFU instead).
 *
 * Who offers is decided by the room rules (the person already in the room offers to the newcomer), so the two
 * phones never both make an offer. [offerer] is that role; only the offerer makes ICE restarts.
 *
 * Every method must be called on the main thread. WebRTC callbacks arrive on its own threads and are moved to
 * [scope] (main) before touching any state.
 */
class PeerSession(
    override val remoteUserId: Long,
    override val offerer: Boolean,
    private val media: LocalMedia,
    iceServers: List<IceServerConfig>,
    private val scope: CoroutineScope,
    private val send: (SignalPayload) -> Unit,
    private val events: PeerEvents
) : PeerLink {

    private val pc: PeerConnection
    private val pendingCandidates = mutableListOf<IceCandidate>()   // came before the remote description
    private var remoteDescriptionSet = false
    private var closed = false
    var iceState: PeerConnection.IceConnectionState = PeerConnection.IceConnectionState.NEW
        private set
    override val isConnected: Boolean
        get() = iceState == PeerConnection.IceConnectionState.CONNECTED || iceState == PeerConnection.IceConnectionState.COMPLETED

    // Declared before init: WebRTC gets it in createPeerConnection below.
    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                if (!closed) send(SignalPayload.Candidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp))
            }
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            scope.launch {
                if (closed) return@launch
                iceState = state
                Log.i(TAG, "ice=$state peer=$remoteUserId")
                events.onIceState(this@PeerSession, state.toIceState())
            }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) scope.launch { if (!closed) events.onRemoteVideo(this@PeerSession, track) }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}        // tracks are fixed for the whole call: no renegotiation
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
    }

    init {
        val servers = iceServers.map { s ->
            PeerConnection.IceServer.builder(s.urls).apply {
                if (s.username != null) setUsername(s.username)
                if (s.credential != null) setPassword(s.credential)
            }.createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY   // new networks (Wi-Fi -> 4G) are found
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            keyType = PeerConnection.KeyType.ECDSA
        }
        pc = media.factory.createPeerConnection(config, observer)
            ?: throw IllegalStateException("Could not create the video connection")

        // Always one audio and one video line, even without my own mic/camera, so I can still RECEIVE both.
        val stream = listOf("skillswap")
        val audio = media.audioTrack
        if (audio != null) pc.addTrack(audio, stream)
        else pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        val video = media.videoTrack
        if (video != null) pc.addTrack(video, stream)
        else pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
    }

    /** Offerer: make and send an offer. [iceRestart] = find a new network path, keep everything else. */
    override suspend fun makeOffer(iceRestart: Boolean) {
        if (closed) return
        try {
            val constraints = MediaConstraints().apply {
                if (iceRestart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            val offer = pc.create(offer = true, constraints)
            if (closed) return
            pc.setLocal(offer)
            send(SignalPayload.Offer(offer.description, iceRestart))
        } catch (e: Exception) {
            if (!closed) events.onNegotiationError(this, "offer: ${e.message}")
        }
    }

    /** Answerer: apply the other phone's offer and send my answer. */
    override suspend fun acceptOffer(sdp: String) {
        if (closed) return
        try {
            pc.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp))
            onRemoteDescriptionApplied()
            val answer = pc.create(offer = false, MediaConstraints())
            if (closed) return
            pc.setLocal(answer)
            send(SignalPayload.Answer(answer.description))
        } catch (e: Exception) {
            if (!closed) events.onNegotiationError(this, "answer: ${e.message}")
        }
    }

    /** Offerer: the other phone's answer arrived. A late or repeated answer is ignored. */
    override suspend fun acceptAnswer(sdp: String) {
        if (closed || pc.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) return
        try {
            pc.setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
            onRemoteDescriptionApplied()
        } catch (e: Exception) {
            if (!closed) events.onNegotiationError(this, "apply answer: ${e.message}")
        }
    }

    /** A network address of the other phone. Kept until the remote description is set (they can arrive first). */
    override fun addRemoteCandidate(c: SignalPayload.Candidate) {
        if (closed) return
        val candidate = IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
        if (!remoteDescriptionSet) {
            if (pendingCandidates.size < MAX_PENDING) pendingCandidates += candidate
            return
        }
        if (!pc.addIceCandidate(candidate)) Log.w(TAG, "an ICE candidate was not accepted")
    }

    private fun onRemoteDescriptionApplied() {
        remoteDescriptionSet = true
        pendingCandidates.forEach { pc.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    /** Stops the connection and frees it. Safe to call twice. */
    override fun close() {
        if (closed) return
        closed = true
        events.onRemoteVideo(this, null)
        pc.dispose()             // also closes it; my own tracks stay alive (they belong to LocalMedia)
    }

    private companion object {
        const val TAG = "VideoRoom"
        const val MAX_PENDING = 64
    }
}

private fun PeerConnection.IceConnectionState.toIceState(): IceState = when (this) {
    PeerConnection.IceConnectionState.NEW -> IceState.NEW
    PeerConnection.IceConnectionState.CHECKING -> IceState.CHECKING
    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> IceState.CONNECTED
    PeerConnection.IceConnectionState.DISCONNECTED -> IceState.DISCONNECTED
    PeerConnection.IceConnectionState.FAILED -> IceState.FAILED
    PeerConnection.IceConnectionState.CLOSED -> IceState.CLOSED
}

// ---- WebRTC's callback API as suspend functions ----

private suspend fun PeerConnection.create(offer: Boolean, constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) { if (cont.isActive) cont.resume(desc) }
            override fun onCreateFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "create failed"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }
        if (offer) createOffer(obs, constraints) else createAnswer(obs, constraints)
    }

private suspend fun PeerConnection.setDescription(local: Boolean, desc: SessionDescription): Unit =
    suspendCancellableCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "set failed"))
            }
        }
        if (local) setLocalDescription(obs, desc) else setRemoteDescription(obs, desc)
    }

private suspend fun PeerConnection.setLocal(desc: SessionDescription) = setDescription(true, desc)
private suspend fun PeerConnection.setRemote(desc: SessionDescription) = setDescription(false, desc)

package uz.skillswap.app.call

/**
 * The life of one video lesson call, as a pure function: (state, what happened) -> new state.
 * No Android, no WebRTC, no timers: [VideoRoomManager] feeds it inputs and shows its phase. Unit-tested.
 *
 *   IDLE -> CREATING -> JOINING -> WAITING (alone) <-> CONNECTING -> CONNECTED <-> RECONNECTING
 *   any active phase -> ENDING (I hang up) -> ENDED;   any active phase -> ENDED (room closed) / FAILED (error)
 */
enum class CallPhase {
    IDLE, CREATING, JOINING, WAITING, CONNECTING, CONNECTED, RECONNECTING, ENDING, ENDED, FAILED;

    /** true while a call is running (camera, microphone and network in use). */
    val isActive: Boolean get() = this != IDLE && this != ENDED && this != FAILED
}

sealed interface CallInput {
    /** I open the lesson's room (also "Try again" after ENDED / FAILED). */
    data object Start : CallInput
    /** The server gave me the room. */
    data object RoomOpened : CallInput
    /** The room stream is open (first event room-state). [peerOnline] = the other person is in the room. */
    data class Joined(val peerOnline: Boolean) : CallInput
    /** The room stream broke (no internet, server restart); the manager reconnects. */
    data object SignalingLost : CallInput
    data object PeerArrived : CallInput
    data object PeerGone : CallInput
    /** Audio/video flow between the phones (ICE connected). */
    data object MediaUp : CallInput
    /** The media path broke (ICE disconnected/failed); WebRTC or an ICE restart may bring it back. */
    data object MediaDown : CallInput
    /** I press the red button. */
    data object HangUp : CallInput
    /** The room is closed (I left, the other person ended it, it expired, the lesson was cancelled...). */
    data class Ended(val reason: EndReason) : CallInput
    data class Failed(val message: String) : CallInput
}

data class CallMachine(
    val phase: CallPhase = CallPhase.IDLE,
    val inRoom: Boolean = false,          // got room-state at least once
    val signalingUp: Boolean = false,
    val peerPresent: Boolean = false,
    val mediaUp: Boolean = false,
    val everConnected: Boolean = false,   // media was up with this peer (so a drop is "reconnecting", not "connecting")
    val endReason: EndReason? = null,
    val error: String? = null
) {
    fun next(input: CallInput): CallMachine {
        // Finished calls only react to a new start.
        if (!phase.isActive && phase != CallPhase.ENDING) {
            return if (input == CallInput.Start) CallMachine(phase = CallPhase.CREATING) else this
        }
        if (phase == CallPhase.ENDING) {
            return when (input) {
                is CallInput.Ended -> copy(phase = CallPhase.ENDED, endReason = input.reason, mediaUp = false)
                is CallInput.Failed -> copy(phase = CallPhase.ENDED, endReason = EndReason.LEFT, mediaUp = false)
                else -> this
            }
        }
        val s = when (input) {
            CallInput.Start -> return this                                   // already running
            CallInput.HangUp -> return copy(phase = CallPhase.ENDING)
            is CallInput.Ended -> return copy(phase = CallPhase.ENDED, endReason = input.reason, mediaUp = false)
            is CallInput.Failed -> return copy(phase = CallPhase.FAILED, error = input.message, mediaUp = false)
            CallInput.RoomOpened -> return if (phase == CallPhase.CREATING) copy(phase = CallPhase.JOINING) else this
            is CallInput.Joined -> copy(inRoom = true, signalingUp = true, peerPresent = input.peerOnline)
            CallInput.SignalingLost -> copy(signalingUp = false)
            CallInput.PeerArrived -> copy(peerPresent = true)
            CallInput.PeerGone -> copy(peerPresent = false, mediaUp = false, everConnected = false)
            CallInput.MediaUp -> copy(mediaUp = true, everConnected = true, peerPresent = true)
            CallInput.MediaDown -> copy(mediaUp = false)
        }
        return s.copy(phase = s.derivedPhase())
    }

    private fun derivedPhase(): CallPhase = when {
        !inRoom -> phase                                       // still CREATING / JOINING
        mediaUp && peerPresent -> CallPhase.CONNECTED          // media flows: that is what the person feels
        !signalingUp -> CallPhase.RECONNECTING
        !peerPresent -> CallPhase.WAITING
        everConnected -> CallPhase.RECONNECTING
        else -> CallPhase.CONNECTING
    }
}

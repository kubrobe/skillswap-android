package uz.skillswap.app.call

/**
 * How a call talks to the server, separate from WebRTC (which carries the media phone to phone).
 * Today: our server's SSE room stream + POSTs. A WebSocket version, or an SFU's signalling, would replace only
 * this class.
 */
interface SignalingClient {
    /** Opens (or returns) the room of a booked lesson. */
    suspend fun openRoom(bookingId: Long): RoomInfo

    /** Streams room events until the server closes the stream (returns) or the line breaks (throws). */
    suspend fun listen(roomId: String, onEvent: suspend (RoomEvent) -> Unit)

    suspend fun send(roomId: String, signal: OutgoingSignal)
    suspend fun leave(roomId: String)
    suspend fun end(roomId: String)
}

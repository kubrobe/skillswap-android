package uz.skillswap.app.call

import uz.skillswap.app.data.SkillSwapRepository

/** The real one: through [SkillSwapRepository], which adds the session token and handles an expired login. */
class ServerSignaling(private val repo: SkillSwapRepository) : SignalingClient {
    override suspend fun openRoom(bookingId: Long): RoomInfo = Protocol.parseRoomInfo(repo.openRoom(bookingId))

    override suspend fun listen(roomId: String, onEvent: suspend (RoomEvent) -> Unit) {
        repo.roomEvents(roomId) { type, json -> Protocol.parseEvent(type, json)?.let { onEvent(it) } }
    }

    override suspend fun send(roomId: String, signal: OutgoingSignal) = repo.sendSignal(roomId, Protocol.signalJson(signal))
    override suspend fun leave(roomId: String) = repo.leaveRoom(roomId)
    override suspend fun end(roomId: String) = repo.endRoom(roomId)
}

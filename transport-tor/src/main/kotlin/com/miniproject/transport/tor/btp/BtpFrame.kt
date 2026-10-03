package com.miniproject.transport.tor.btp

data class BtpFrame(
    val version: Byte = BtpLimits.PROTOCOL_VERSION,
    val type: BtpFrameType,
    val streamId: Int = 0,
    val sequenceNumber: Long = 0L,
    val msgId: ByteArray = ByteArray(16),
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val payload: ByteArray = ByteArray(0)
) {
    init {
        require(msgId.size == 16) { "msgId must be exactly 16 bytes (was ${msgId.size})" }
        require(chunkIndex >= 0) { "chunkIndex must be >= 0 (was $chunkIndex)" }
        require(totalChunks >= 1) { "totalChunks must be >= 1 (was $totalChunks)" }
        if (type == BtpFrameType.DATA) {
            require(chunkIndex < totalChunks) { "chunkIndex ($chunkIndex) must be < totalChunks ($totalChunks)" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BtpFrame

        if (version != other.version) return false
        if (type != other.type) return false
        if (streamId != other.streamId) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (!msgId.contentEquals(other.msgId)) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + streamId
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + msgId.contentHashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        val msgIdHex = msgId.take(4).joinToString("") { "%02x".format(it) }
        return "BtpFrame(type=$type, seq=$sequenceNumber, msgId=${msgIdHex}..., chunk=$chunkIndex/$totalChunks, payloadLen=${payload.size})"
    }
}

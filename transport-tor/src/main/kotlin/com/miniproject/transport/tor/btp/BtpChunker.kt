package com.miniproject.transport.tor.btp

import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.math.ceil

object BtpChunker {

    private val secureRandom = SecureRandom()

    /**
     * Splits an arbitrary payload into chunked BtpFrames of type DATA.
     *
     * @param payload the data to chunk
     * @param maxChunkSize the maximum payload bytes per frame (e.g. 512 for BLE, 16384 for Tor)
     * @param msgId optional 16-byte message ID; if null or empty, random 16 bytes are generated
     * @param startingSeqNum sequence number for the first chunk frame
     * @param streamId stream identifier for multiplexing
     * @return list of BtpFrames ready for serialization and transmission
     */
    fun chunkMessage(
        payload: ByteArray,
        maxChunkSize: Int = BtpLimits.DEFAULT_MAX_PAYLOAD,
        msgId: ByteArray? = null,
        startingSeqNum: Long = 1L,
        streamId: Int = 0
    ): List<BtpFrame> {
        require(maxChunkSize > 0) { "maxChunkSize must be > 0" }

        val actualMsgId = if (msgId != null && msgId.size == 16) {
            msgId.copyOf()
        } else {
            ByteArray(16).also { secureRandom.nextBytes(it) }
        }

        if (payload.isEmpty()) {
            return listOf(
                BtpFrame(
                    version = BtpLimits.PROTOCOL_VERSION,
                    type = BtpFrameType.DATA,
                    streamId = streamId,
                    sequenceNumber = startingSeqNum,
                    msgId = actualMsgId,
                    chunkIndex = 0,
                    totalChunks = 1,
                    payload = ByteArray(0)
                )
            )
        }

        val totalChunks = ceil(payload.size / maxChunkSize.toDouble()).toInt().coerceAtLeast(1)
        val frames = ArrayList<BtpFrame>(totalChunks)

        var seq = startingSeqNum
        for (i in 0 until totalChunks) {
            val from = i * maxChunkSize
            val to = minOf(from + maxChunkSize, payload.size)
            val chunkPayload = payload.copyOfRange(from, to)

            frames.add(
                BtpFrame(
                    version = BtpLimits.PROTOCOL_VERSION,
                    type = BtpFrameType.DATA,
                    streamId = streamId,
                    sequenceNumber = seq++,
                    msgId = actualMsgId,
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    payload = chunkPayload
                )
            )
        }

        return frames
    }

    /**
     * Creates an ACK frame confirming receipt of a chunk or message.
     */
    fun createAckFrame(
        msgId: ByteArray,
        ackedChunkIndex: Int,
        seqNum: Long,
        totalChunks: Int = 1,
        streamId: Int = 0
    ): BtpFrame {
        require(msgId.size == 16) { "msgId must be exactly 16 bytes" }
        val payload = ByteBuffer.allocate(2).putShort(ackedChunkIndex.toShort()).array()
        return BtpFrame(
            type = BtpFrameType.ACK,
            streamId = streamId,
            sequenceNumber = seqNum,
            msgId = msgId,
            chunkIndex = ackedChunkIndex,
            totalChunks = maxOf(totalChunks, ackedChunkIndex + 1),
            payload = payload
        )
    }

    /**
     * Creates a PING frame with a timestamp.
     */
    fun createPingFrame(
        seqNum: Long,
        timestampMs: Long = System.currentTimeMillis(),
        streamId: Int = 0
    ): BtpFrame {
        val payload = ByteBuffer.allocate(8).putLong(timestampMs).array()
        return BtpFrame(
            type = BtpFrameType.PING,
            streamId = streamId,
            sequenceNumber = seqNum,
            payload = payload
        )
    }

    /**
     * Creates a PONG frame replying to a PING.
     */
    fun createPongFrame(
        seqNum: Long,
        timestampMs: Long,
        streamId: Int = 0
    ): BtpFrame {
        val payload = ByteBuffer.allocate(8).putLong(timestampMs).array()
        return BtpFrame(
            type = BtpFrameType.PONG,
            streamId = streamId,
            sequenceNumber = seqNum,
            payload = payload
        )
    }

    /**
     * Creates a REKEY frame containing a new ephemeral X25519 public key.
     */
    fun createRekeyFrame(
        seqNum: Long,
        ephemeralPubkey: ByteArray,
        streamId: Int = 0
    ): BtpFrame {
        require(ephemeralPubkey.size == 32) { "X25519 public key must be 32 bytes" }
        return BtpFrame(
            type = BtpFrameType.REKEY,
            streamId = streamId,
            sequenceNumber = seqNum,
            payload = ephemeralPubkey.copyOf()
        )
    }

    /**
     * Creates a CLOSE frame for orderly session shutdown.
     */
    fun createCloseFrame(
        seqNum: Long,
        reasonCode: Byte = 0,
        streamId: Int = 0
    ): BtpFrame {
        return BtpFrame(
            type = BtpFrameType.CLOSE,
            streamId = streamId,
            sequenceNumber = seqNum,
            payload = byteArrayOf(reasonCode)
        )
    }

    /**
     * Creates an ERROR frame.
     */
    fun createErrorFrame(
        seqNum: Long,
        errorCode: Short,
        message: String,
        streamId: Int = 0
    ): BtpFrame {
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val payload = ByteBuffer.allocate(2 + msgBytes.size)
            .putShort(errorCode)
            .put(msgBytes)
            .array()
        return BtpFrame(
            type = BtpFrameType.ERROR,
            streamId = streamId,
            sequenceNumber = seqNum,
            payload = payload
        )
    }
}

package com.miniproject.transport.tor.btp

import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class BtpReassembler(
    private val ttlMs: Long = BtpLimits.DEFAULT_REASSEMBLY_TTL_MS
) {

    private class PendingMessage(
        val totalChunks: Int,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val chunks = ConcurrentHashMap<Int, ByteArray>()

        fun isComplete(): Boolean = chunks.size == totalChunks

        fun assemble(): ByteArray {
            val out = ByteArrayOutputStream()
            for (i in 0 until totalChunks) {
                val chunk = chunks[i] ?: throw IllegalStateException("Missing chunk $i of $totalChunks")
                out.write(chunk)
            }
            return out.toByteArray()
        }
    }

    private val pending = ConcurrentHashMap<String, PendingMessage>()

    /**
     * Ingests a DATA frame and attempts reassembly.
     *
     * @param frame the BtpFrame (must be of type DATA)
     * @return the complete reassembled payload if this chunk completes the message, or null if more chunks are awaited
     */
    fun addChunk(frame: BtpFrame): ByteArray? {
        require(frame.type == BtpFrameType.DATA) {
            "Expected DATA frame, received: ${frame.type}"
        }

        // Single-chunk optimization: bypass buffer
        if (frame.totalChunks == 1 && frame.chunkIndex == 0) {
            return frame.payload
        }

        cleanStale()

        val key = toHex(frame.msgId)
        val message = pending.computeIfAbsent(key) {
            PendingMessage(totalChunks = frame.totalChunks)
        }

        require(message.totalChunks == frame.totalChunks) {
            "Total chunks mismatch for msgId $key: expected ${message.totalChunks}, got ${frame.totalChunks}"
        }

        message.chunks[frame.chunkIndex] = frame.payload

        return if (message.isComplete()) {
            pending.remove(key)
            message.assemble()
        } else {
            null
        }
    }

    /**
     * Purges stale incomplete messages exceeding TTL.
     */
    fun cleanStale() {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { now - it.value.createdAt > ttlMs }
    }

    fun pendingCount(): Int = pending.size

    fun clear() {
        pending.clear()
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}

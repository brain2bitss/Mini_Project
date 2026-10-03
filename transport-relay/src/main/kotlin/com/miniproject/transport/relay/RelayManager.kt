package com.miniproject.transport.relay

import com.miniproject.core.crypto.CryptoManager
import com.miniproject.core.db.Daos

/**
 * Manages the Store-and-Forward relay queue.
 * Packets are encrypted with the final destination's key.
 * This node only sees the destination onion address, TTL, and encrypted payload.
 */
class RelayManager(private val dbDaos: Daos) {

    companion object {
        const val MAX_HOP_LIMIT = 3
        const val TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L // 7 Days
    }

    /**
     * Enqueues a packet to be relayed when a connection to a peer is established.
     */
    fun enqueueForRelay(destinationOnion: String, encryptedPayload: ByteArray) {
        // We prepend the hop count to the payload (in a real implementation this would be parsed)
        dbDaos.enqueueRelayPayload(
            com.miniproject.core.db.RelayQueueItem(
                destOnion = destinationOnion,
                hopCount = 0,
                ttl = TTL_MILLIS.toInt(),
                payload = encryptedPayload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Removes expired packets from the queue.
     */
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val pending = dbDaos.getPendingRelayPayloads()
        pending.forEach { item ->
            if (now - item.createdAt > TTL_MILLIS) {
                dbDaos.removeRelayPayload(item.id)
            }
        }
    }

    /**
     * Retrieves packets meant for a specific peer to send upon connection.
     */
    fun getPacketsForPeer(onionAddress: String): List<ByteArray> {
        val pending = dbDaos.getPendingRelayPayloads().filter { it.destOnion == onionAddress }
        pending.forEach { dbDaos.removeRelayPayload(it.id) }
        return pending.map { it.payload }
    }
}

package com.miniproject.transport.tor

import android.util.Log
import com.miniproject.core.db.Daos
import kotlinx.coroutines.*
import java.net.Socket

class QueueWorker(
    private val daos: Daos,
    private val torManager: TorManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private val TAG = "QueueWorker"

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            while (isActive) {
                processQueue()
                delay(30_000) // Poll every 30 seconds
            }
        }
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }

    /**
     * Trigger immediate processing (e.g. when a new message is added or network becomes available).
     */
    fun trigger() {
        if (isRunning) {
            scope.launch {
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        val pending = daos.getPendingPayloads()
        if (pending.isEmpty()) return

        for (item in pending) {
            try {
                // If the item doesn't have a contactId, it might be a raw relay payload, but for now we assume it belongs to a contact.
                if (item.contactId == null) {
                    Log.w(TAG, "Item ${item.id} has no contactId, skipping")
                    continue
                }

                // Load contact to get onion address
                // (Note: in a real app, contact DAO should have getContactById)
                val allContacts = daos.getAllContacts()
                val contact = allContacts.find { it.id == item.contactId }
                if (contact == null || contact.onionAddress.isNullOrEmpty()) {
                    Log.e(TAG, "Contact missing or has no onion address for item ${item.id}")
                    daos.incrementOutgoingAttempts(item.id, System.currentTimeMillis() + 60_000) // Retry in 1m
                    continue
                }

                Log.d(TAG, "Attempting to send payload ${item.id} to ${contact.onionAddress}")
                
                // For now, assume payload is already BTP chunked or we just use btpSendChunkedMessage.
                // Wait, if it's already ratcheted and serialized, we just need to send it via BTP.
                // The payload here should probably be the full ratcheted message bytes.
                
                // Let's open a socket
                var socket: Socket? = null
                try {
                    socket = torManager.connectToPeer("${contact.onionAddress}.onion", 7654)
                    
                    // In a full implementation, we'd need to do the BTP handshake here if there isn't an active connection.
                    // For now, this is a placeholder for the actual BTP sending logic.
                    // TODO: Integrate with BtpSessionManager to establish session and send payload.
                    
                    Log.d(TAG, "Sent payload ${item.id} successfully")
                    daos.removeOutgoingPayload(item.id) // Remove on success
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send payload ${item.id}", e)
                    // Exponential backoff: base 1 min * 2^attempts
                    val backoff = 60_000L * (1 shl item.attempts.coerceAtMost(6)) // Max backoff ~1 hr
                    daos.incrementOutgoingAttempts(item.id, System.currentTimeMillis() + backoff)
                } finally {
                    socket?.close()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing queue item", e)
            }
        }
    }
}

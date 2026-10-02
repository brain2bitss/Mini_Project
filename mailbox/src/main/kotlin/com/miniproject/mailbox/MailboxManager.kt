package com.miniproject.mailbox

import com.miniproject.core.crypto.CryptoManager
import com.miniproject.transport.tor.TorManager
import kotlin.concurrent.thread

/**
 * Mailbox feature:
 * A secondary device (like a Raspberry Pi or always-on phone) acts as a mailbox.
 * It receives encrypted messages while the primary device is offline.
 */
class MailboxManager(private val torManager: TorManager) {

    private var isMailboxModeEnabled = false

    fun startMailboxMode() {
        isMailboxModeEnabled = true
        torManager.listenForPeers { socket ->
            thread {
                try {
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    // 1. Authenticate that the connection is from our primary device or an authorized peer
                    // 2. Receive encrypted payloads
                    // 3. Store payloads to local DB
                    // (Implementation omitted for MVP brevity)
                } catch (e: Exception) {
                    // Handle socket errors
                } finally {
                    socket.close()
                }
            }
        }
    }

    fun syncFromMailbox(mailboxOnion: String) {
        thread {
            val socket = torManager.connectToPeer(mailboxOnion)
            try {
                // Fetch stored messages from the mailbox
                // (Implementation omitted for MVP brevity)
            } finally {
                socket.close()
            }
        }
    }
}

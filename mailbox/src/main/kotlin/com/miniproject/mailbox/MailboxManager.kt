package com.miniproject.mailbox

import com.miniproject.core.db.Daos
import com.miniproject.core.db.MailboxQueueItem
import com.miniproject.transport.tor.TorManager
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mailbox feature:
 * A secondary device (like a Raspberry Pi or always-on phone) acts as a mailbox.
 * It receives encrypted messages while the primary device is offline.
 */
class MailboxManager(
    private val torManager: TorManager,
    private val dbDaos: Daos
) {
    companion object {
        const val CMD_STORE = 1
        const val CMD_FETCH = 2
    }

    var isMailboxModeEnabled = false
        private set

    /**
     * Enables Mailbox Server mode. 
     * In a real app this would start a dedicated listener. Here we just expose the logic.
     * The TorManager's listener should route incoming sockets here if they are mailbox commands.
     */
    fun enableMailboxMode() {
        isMailboxModeEnabled = true
    }

    /**
     * Handles an incoming connection on a Mailbox Server.
     */
    suspend fun handleIncomingConnection(socket: Socket) = withContext(Dispatchers.IO) {
        if (!isMailboxModeEnabled) return@withContext
        
        try {
            val din = DataInputStream(socket.getInputStream())
            val dout = DataOutputStream(socket.getOutputStream())

            val command = din.readInt()
            if (command == CMD_STORE) {
                val destOnion = din.readUTF()
                val payloadSize = din.readInt()
                if (payloadSize in 1..(1024 * 1024)) {
                    val payload = ByteArray(payloadSize)
                    din.readFully(payload)
                    
                    dbDaos.enqueueMailboxPayload(
                        MailboxQueueItem(
                            destOnion = destOnion,
                            payload = payload
                        )
                    )
                }
            } else if (command == CMD_FETCH) {
                val destOnion = din.readUTF()
                // In a real app, we must cryptographically authenticate the fetcher owns this onion!
                // For MVP, we skip authentication.
                val payloads = dbDaos.getPendingMailboxPayloads().filter { it.destOnion == destOnion }
                
                dout.writeInt(payloads.size)
                for (item in payloads) {
                    dout.writeInt(item.payload.size)
                    dout.write(item.payload)
                    dbDaos.removeMailboxPayload(item.id)
                }
                dout.flush()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            socket.close()
        }
    }

    /**
     * As a client, fetch waiting messages from a specific mailbox node.
     */
    suspend fun fetchFromMailbox(mailboxOnion: String, myOnion: String): List<ByteArray> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ByteArray>()
        var socket: Socket? = null
        try {
            socket = torManager.connectToPeer("$mailboxOnion.onion", 7654)
            val dout = DataOutputStream(socket.getOutputStream())
            val din = DataInputStream(socket.getInputStream())

            dout.writeInt(CMD_FETCH)
            dout.writeUTF(myOnion)
            dout.flush()

            val count = din.readInt()
            for (i in 0 until count) {
                val size = din.readInt()
                if (size in 1..(1024 * 1024)) {
                    val payload = ByteArray(size)
                    din.readFully(payload)
                    results.add(payload)
                }
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            socket?.close()
        }
        return@withContext results
    }

    /**
     * As a client, send a message to a recipient's designated mailbox.
     */
    suspend fun storeToMailbox(mailboxOnion: String, destOnion: String, payload: ByteArray) = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = torManager.connectToPeer("$mailboxOnion.onion", 7654)
            val dout = DataOutputStream(socket.getOutputStream())

            dout.writeInt(CMD_STORE)
            dout.writeUTF(destOnion)
            dout.writeInt(payload.size)
            dout.write(payload)
            dout.flush()
        } catch (e: Exception) {
            // ignore
        } finally {
            socket?.close()
        }
    }
}

package com.miniproject.transport.relay

import com.miniproject.core.db.Daos
import com.miniproject.core.db.RelayQueueItem
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RelayProtocol(private val dbDaos: Daos) {

    companion object {
        const val MAX_HOP_LIMIT = 3
    }

    suspend fun syncRelays(inputStream: InputStream, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val din = DataInputStream(inputStream)
        val dout = DataOutputStream(outputStream)

        // 1. Send all our pending relays
        val myRelays = dbDaos.getPendingRelayPayloads()
        dout.writeInt(myRelays.size)
        for (relay in myRelays) {
            dout.writeUTF(relay.destOnion)
            dout.writeInt(relay.hopCount + 1)
            dout.writeInt(relay.ttl)
            dout.writeLong(relay.createdAt)
            dout.writeInt(relay.payload.size)
            dout.write(relay.payload)
        }
        dout.flush()

        // 2. Receive peer's relays
        val peerRelayCount = din.readInt()
        for (i in 0 until peerRelayCount) {
            val destOnion = din.readUTF()
            val hopCount = din.readInt()
            val ttl = din.readInt()
            val createdAt = din.readLong()
            val payloadSize = din.readInt()
            
            // Limit payload size to avoid OOM
            if (payloadSize > 1024 * 1024) {
                // skip or throw
                break
            }
            
            val payload = ByteArray(payloadSize)
            din.readFully(payload)

            if (hopCount <= MAX_HOP_LIMIT) {
                // Save to our queue
                dbDaos.enqueueRelayPayload(
                    RelayQueueItem(
                        destOnion = destOnion,
                        hopCount = hopCount,
                        ttl = ttl,
                        payload = payload,
                        createdAt = createdAt
                    )
                )
            }
        }
    }
}

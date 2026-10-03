package com.miniproject.core.db

import com.miniproject.core.crypto.CryptoManager

class MessageRepository(
    private val daos: Daos,
    private val dbEncryptionKey: ByteArray
) {
    init {
        require(dbEncryptionKey.size == 32) { "DB encryption key must be 32 bytes for AES-256-GCM" }
    }

    /**
     * Saves an incoming or outgoing message to the database, encrypting its body.
     */
    fun saveMessage(
        contactId: Long?,
        direction: Int, // 0 = IN, 1 = OUT
        state: MessageState,
        bodyPlaintext: String,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val bodyBytes = bodyPlaintext.toByteArray(Charsets.UTF_8)
        val encryptedBody = CryptoManager.encryptAesGcm(dbEncryptionKey, bodyBytes)

        val message = Message(
            contactId = contactId,
            direction = direction,
            state = state,
            bodyEncrypted = encryptedBody,
            timestamp = timestamp
        )
        return daos.insertMessage(message)
    }

    /**
     * Retrieves messages for a contact, decrypting their bodies.
     */
    fun getMessagesForContact(contactId: Long): List<DecryptedMessage> {
        val messages = daos.getMessagesForContact(contactId)
        return messages.mapNotNull { msg ->
            try {
                val decryptedBytes = CryptoManager.decryptAesGcm(dbEncryptionKey, msg.bodyEncrypted)
                val bodyPlaintext = String(decryptedBytes, Charsets.UTF_8)
                DecryptedMessage(
                    id = msg.id,
                    contactId = msg.contactId,
                    forumThreadId = msg.forumThreadId,
                    direction = msg.direction,
                    state = msg.state,
                    bodyPlaintext = bodyPlaintext,
                    timestamp = msg.timestamp,
                    deliveredAt = msg.deliveredAt,
                    readAt = msg.readAt,
                    expiryAt = msg.expiryAt
                )
            } catch (e: Exception) {
                // If decryption fails, we might want to log it and skip, or return an error indicator
                null
            }
        }
    }

    /**
     * Updates a message's state (e.g., from QUEUED to SENT, or SENT to ACKED).
     */
    fun updateMessageState(messageId: Long, newState: MessageState) {
        daos.updateMessageState(messageId, newState)
    }
}

data class DecryptedMessage(
    val id: Long,
    val contactId: Long?,
    val forumThreadId: Long?,
    val direction: Int,
    val state: MessageState,
    val bodyPlaintext: String,
    val timestamp: Long,
    val deliveredAt: Long?,
    val readAt: Long?,
    val expiryAt: Long?
)

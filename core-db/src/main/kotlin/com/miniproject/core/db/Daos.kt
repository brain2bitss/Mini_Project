package com.miniproject.core.db

import android.content.ContentValues
import net.zetetic.database.sqlcipher.SQLiteDatabase

data class Contact(val id: Long = 0, val alias: String, val publicKey: ByteArray, val onionAddress: String?, val status: Int = 0)
data class Message(val id: Long = 0, val contactId: Long, val body: String, val timestamp: Long, val isSent: Boolean, val isRead: Boolean)
data class TransportQueueItem(val id: Long = 0, val destinationOnion: String, val payload: ByteArray, val priority: Int = 0, val timestamp: Long)

class Daos(private val db: SQLiteDatabase) {

    // --- Contact DAO ---
    fun insertContact(contact: Contact): Long {
        val values = ContentValues().apply {
            put("alias", contact.alias)
            put("public_key", contact.publicKey)
            put("onion_address", contact.onionAddress)
            put("status", contact.status)
        }
        return db.insert(DatabaseHelper.TABLE_CONTACTS, null, values)
    }

    fun getAllContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        db.query(DatabaseHelper.TABLE_CONTACTS, null, null, null, null, null, "alias ASC").use { cursor ->
            while (cursor.moveToNext()) {
                contacts.add(
                    Contact(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        alias = cursor.getString(cursor.getColumnIndexOrThrow("alias")),
                        publicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("public_key")),
                        onionAddress = cursor.getString(cursor.getColumnIndexOrThrow("onion_address")),
                        status = cursor.getInt(cursor.getColumnIndexOrThrow("status"))
                    )
                )
            }
        }
        return contacts
    }

    // --- Message DAO ---
    fun insertMessage(message: Message): Long {
        val values = ContentValues().apply {
            put("contact_id", message.contactId)
            put("body", message.body)
            put("timestamp", message.timestamp)
            put("is_sent", if (message.isSent) 1 else 0)
            put("is_read", if (message.isRead) 1 else 0)
        }
        return db.insert(DatabaseHelper.TABLE_MESSAGES, null, values)
    }

    fun getMessagesForContact(contactId: Long): List<Message> {
        val messages = mutableListOf<Message>()
        db.query(DatabaseHelper.TABLE_MESSAGES, null, "contact_id = ?", arrayOf(contactId.toString()), null, null, "timestamp ASC").use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(
                    Message(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id")),
                        body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        isSent = cursor.getInt(cursor.getColumnIndexOrThrow("is_sent")) == 1,
                        isRead = cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1
                    )
                )
            }
        }
        return messages
    }

    // --- Transport Queue DAO ---
    fun enqueuePayload(item: TransportQueueItem): Long {
        val values = ContentValues().apply {
            put("destination_onion", item.destinationOnion)
            put("payload", item.payload)
            put("priority", item.priority)
            put("timestamp", item.timestamp)
        }
        return db.insert(DatabaseHelper.TABLE_TRANSPORT_QUEUE, null, values)
    }

    fun getPendingPayloads(): List<TransportQueueItem> {
        val items = mutableListOf<TransportQueueItem>()
        db.query(DatabaseHelper.TABLE_TRANSPORT_QUEUE, null, null, null, null, null, "priority DESC, timestamp ASC").use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    TransportQueueItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        destinationOnion = cursor.getString(cursor.getColumnIndexOrThrow("destination_onion")),
                        payload = cursor.getBlob(cursor.getColumnIndexOrThrow("payload")),
                        priority = cursor.getInt(cursor.getColumnIndexOrThrow("priority")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return items
    }
    
    fun removePayload(id: Long) {
        db.delete(DatabaseHelper.TABLE_TRANSPORT_QUEUE, "id = ?", arrayOf(id.toString()))
    }
}

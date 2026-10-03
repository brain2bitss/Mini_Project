package com.miniproject.core.db

import android.content.ContentValues
import net.sqlcipher.database.SQLiteDatabase

// --- Data Classes ---

data class Identity(
    val id: Long = 0,
    val nickname: String,
    val pubkey: ByteArray,
    val privkeyEncrypted: ByteArray,
    val onionAddress: String?,
    val createdAt: Long = System.currentTimeMillis()
)

data class Contact(
    val id: Long = 0,
    val alias: String,
    val pubkey: ByteArray,
    val onionAddress: String?,
    val relayEnabled: Boolean = false,
    val mailboxAddress: String? = null,
    val lastSeenAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Session(
    val id: Long = 0,
    val contactId: Long,
    val sessionId: ByteArray,
    val sendChainKey: ByteArray,
    val receiveChainKey: ByteArray,
    val rootKey: ByteArray,
    val sendCount: Int = 0,
    val receiveCount: Int = 0,
    val openedAt: Long = System.currentTimeMillis()
)

enum class MessageState(val code: Int) {
    DRAFT(0), QUEUED(1), SENT(2), ACKED(3), DELIVERED(4), FAILED(5);
    companion object {
        fun fromCode(code: Int): MessageState = entries.firstOrNull { it.code == code } ?: DRAFT
    }
}

data class Message(
    val id: Long = 0,
    val contactId: Long?,
    val forumThreadId: Long? = null,
    val direction: Int, // 0=in, 1=out
    val state: MessageState,
    val bodyEncrypted: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val expiryAt: Long? = null
)

data class Forum(
    val id: Long = 0,
    val title: String,
    val description: String?,
    val creatorPublicKey: ByteArray
)

data class ForumPost(
    val id: Long = 0,
    val forumId: Long,
    val authorPublicKey: ByteArray,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Blog(
    val id: Long = 0,
    val authorPublicKey: ByteArray,
    val title: String
)

data class BlogPost(
    val id: Long = 0,
    val blogId: Long,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OutgoingQueueItem(
    val id: Long = 0,
    val contactId: Long?,
    val payload: ByteArray,
    val state: Int = 0, // 0=QUEUED, 1=IN_FLIGHT
    val attempts: Int = 0,
    val nextAttemptAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// --- DAO Implementation ---

class Daos(private val db: SQLiteDatabase) {

    // --- Identity DAO ---
    fun insertIdentity(identity: Identity): Long {
        val values = ContentValues().apply {
            put("nickname", identity.nickname)
            put("pubkey", identity.pubkey)
            put("privkey_encrypted", identity.privkeyEncrypted)
            put("onion_address", identity.onionAddress)
            put("created_at", identity.createdAt)
        }
        return db.insert(DatabaseHelper.TABLE_IDENTITY, null, values)
    }

    fun getIdentity(): Identity? {
        db.query(DatabaseHelper.TABLE_IDENTITY, null, null, null, null, null, "id ASC", "1").use { cursor ->
            if (cursor.moveToFirst()) {
                return Identity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    nickname = cursor.getString(cursor.getColumnIndexOrThrow("nickname")),
                    pubkey = cursor.getBlob(cursor.getColumnIndexOrThrow("pubkey")),
                    privkeyEncrypted = cursor.getBlob(cursor.getColumnIndexOrThrow("privkey_encrypted")),
                    onionAddress = cursor.getString(cursor.getColumnIndexOrThrow("onion_address")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return null
    }

    // --- Contact DAO ---
    fun insertContact(contact: Contact): Long {
        val values = ContentValues().apply {
            put("alias", contact.alias)
            put("pubkey", contact.pubkey)
            put("onion_address", contact.onionAddress)
            put("relay_enabled", if (contact.relayEnabled) 1 else 0)
            put("mailbox_address", contact.mailboxAddress)
            put("last_seen_at", contact.lastSeenAt)
            put("created_at", contact.createdAt)
        }
        return db.insert(DatabaseHelper.TABLE_CONTACTS, null, values)
    }

    fun getAllContacts(): List<Contact> {
        val contacts = mutableListOf<Contact>()
        db.query(DatabaseHelper.TABLE_CONTACTS, null, null, null, null, null, "alias ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val lastSeenIndex = cursor.getColumnIndexOrThrow("last_seen_at")
                contacts.add(
                    Contact(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        alias = cursor.getString(cursor.getColumnIndexOrThrow("alias")),
                        pubkey = cursor.getBlob(cursor.getColumnIndexOrThrow("pubkey")),
                        onionAddress = cursor.getString(cursor.getColumnIndexOrThrow("onion_address")),
                        relayEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("relay_enabled")) == 1,
                        mailboxAddress = cursor.getString(cursor.getColumnIndexOrThrow("mailbox_address")),
                        lastSeenAt = if (cursor.isNull(lastSeenIndex)) null else cursor.getLong(lastSeenIndex),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return contacts
    }

    // --- Session DAO ---
    fun insertSession(session: Session): Long {
        val values = ContentValues().apply {
            put("contact_id", session.contactId)
            put("session_id", session.sessionId)
            put("send_chain_key", session.sendChainKey)
            put("receive_chain_key", session.receiveChainKey)
            put("root_key", session.rootKey)
            put("send_count", session.sendCount)
            put("receive_count", session.receiveCount)
            put("opened_at", session.openedAt)
        }
        return db.insert(DatabaseHelper.TABLE_SESSIONS, null, values)
    }

    fun getSessionForContact(contactId: Long): Session? {
        db.query(DatabaseHelper.TABLE_SESSIONS, null, "contact_id = ?", arrayOf(contactId.toString()), null, null, "opened_at DESC", "1").use { cursor ->
            if (cursor.moveToFirst()) {
                return Session(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id")),
                    sessionId = cursor.getBlob(cursor.getColumnIndexOrThrow("session_id")),
                    sendChainKey = cursor.getBlob(cursor.getColumnIndexOrThrow("send_chain_key")),
                    receiveChainKey = cursor.getBlob(cursor.getColumnIndexOrThrow("receive_chain_key")),
                    rootKey = cursor.getBlob(cursor.getColumnIndexOrThrow("root_key")),
                    sendCount = cursor.getInt(cursor.getColumnIndexOrThrow("send_count")),
                    receiveCount = cursor.getInt(cursor.getColumnIndexOrThrow("receive_count")),
                    openedAt = cursor.getLong(cursor.getColumnIndexOrThrow("opened_at"))
                )
            }
        }
        return null
    }

    fun updateSessionKeys(sessionId: Long, sendChain: ByteArray, receiveChain: ByteArray, root: ByteArray, sendCount: Int, receiveCount: Int) {
        val values = ContentValues().apply {
            put("send_chain_key", sendChain)
            put("receive_chain_key", receiveChain)
            put("root_key", root)
            put("send_count", sendCount)
            put("receive_count", receiveCount)
        }
        db.update(DatabaseHelper.TABLE_SESSIONS, values, "id = ?", arrayOf(sessionId.toString()))
    }

    // --- Message DAO ---
    fun insertMessage(message: Message): Long {
        val values = ContentValues().apply {
            put("contact_id", message.contactId)
            put("forum_thread_id", message.forumThreadId)
            put("direction", message.direction)
            put("state", message.state.code)
            put("body_encrypted", message.bodyEncrypted)
            put("timestamp", message.timestamp)
            put("delivered_at", message.deliveredAt)
            put("read_at", message.readAt)
            put("expiry_at", message.expiryAt)
        }
        return db.insert(DatabaseHelper.TABLE_MESSAGES, null, values)
    }

    fun updateMessageState(messageId: Long, state: MessageState) {
        val values = ContentValues().apply {
            put("state", state.code)
        }
        db.update(DatabaseHelper.TABLE_MESSAGES, values, "id = ?", arrayOf(messageId.toString()))
    }

    fun getMessagesForContact(contactId: Long): List<Message> {
        val messages = mutableListOf<Message>()
        db.query(DatabaseHelper.TABLE_MESSAGES, null, "contact_id = ?", arrayOf(contactId.toString()), null, null, "timestamp ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val deliveredIndex = cursor.getColumnIndexOrThrow("delivered_at")
                val readIndex = cursor.getColumnIndexOrThrow("read_at")
                val expiryIndex = cursor.getColumnIndexOrThrow("expiry_at")
                val forumIndex = cursor.getColumnIndexOrThrow("forum_thread_id")
                
                messages.add(
                    Message(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        contactId = cursor.getLong(cursor.getColumnIndexOrThrow("contact_id")),
                        forumThreadId = if (cursor.isNull(forumIndex)) null else cursor.getLong(forumIndex),
                        direction = cursor.getInt(cursor.getColumnIndexOrThrow("direction")),
                        state = MessageState.fromCode(cursor.getInt(cursor.getColumnIndexOrThrow("state"))),
                        bodyEncrypted = cursor.getBlob(cursor.getColumnIndexOrThrow("body_encrypted")),
                        timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        deliveredAt = if (cursor.isNull(deliveredIndex)) null else cursor.getLong(deliveredIndex),
                        readAt = if (cursor.isNull(readIndex)) null else cursor.getLong(readIndex),
                        expiryAt = if (cursor.isNull(expiryIndex)) null else cursor.getLong(expiryIndex)
                    )
                )
            }
        }
        return messages
    }

    // --- Outgoing Queue DAO ---
    fun enqueuePayload(item: OutgoingQueueItem): Long {
        val values = ContentValues().apply {
            put("contact_id", item.contactId)
            put("payload", item.payload)
            put("state", item.state)
            put("attempts", item.attempts)
            put("next_attempt_at", item.nextAttemptAt)
            put("created_at", item.createdAt)
        }
        return db.insert(DatabaseHelper.TABLE_OUTGOING_QUEUE, null, values)
    }

    fun getPendingPayloads(): List<OutgoingQueueItem> {
        val items = mutableListOf<OutgoingQueueItem>()
        // Only get items that are QUEUED (0) and whose next_attempt_at is either null or in the past
        val now = System.currentTimeMillis()
        val selection = "state = 0 AND (next_attempt_at IS NULL OR next_attempt_at <= ?)"
        db.query(DatabaseHelper.TABLE_OUTGOING_QUEUE, null, selection, arrayOf(now.toString()), null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val contactIndex = cursor.getColumnIndexOrThrow("contact_id")
                val nextAttemptIndex = cursor.getColumnIndexOrThrow("next_attempt_at")
                items.add(
                    OutgoingQueueItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        contactId = if (cursor.isNull(contactIndex)) null else cursor.getLong(contactIndex),
                        payload = cursor.getBlob(cursor.getColumnIndexOrThrow("payload")),
                        state = cursor.getInt(cursor.getColumnIndexOrThrow("state")),
                        attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts")),
                        nextAttemptAt = if (cursor.isNull(nextAttemptIndex)) null else cursor.getLong(nextAttemptIndex),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return items
    }
    
    fun removeOutgoingPayload(id: Long) {
        db.delete(DatabaseHelper.TABLE_OUTGOING_QUEUE, "id = ?", arrayOf(id.toString()))
    }
    
    fun incrementOutgoingAttempts(id: Long, nextAttemptAt: Long) {
        db.execSQL(
            "UPDATE ${DatabaseHelper.TABLE_OUTGOING_QUEUE} SET attempts = attempts + 1, next_attempt_at = ? WHERE id = ?",
            arrayOf(nextAttemptAt, id)
        )
    }

    // --- Relay Queue DAO ---
    fun enqueueRelayPayload(item: RelayQueueItem): Long {
        val values = ContentValues().apply {
            put("dest_onion", item.destOnion)
            put("hop_count", item.hopCount)
            put("ttl", item.ttl)
            put("payload", item.payload)
            put("created_at", item.createdAt)
        }
        return db.insert(DatabaseHelper.TABLE_RELAY_QUEUE, null, values)
    }

    fun getPendingRelayPayloads(): List<RelayQueueItem> {
        val items = mutableListOf<RelayQueueItem>()
        db.query(DatabaseHelper.TABLE_RELAY_QUEUE, null, null, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    RelayQueueItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        destOnion = cursor.getString(cursor.getColumnIndexOrThrow("dest_onion")),
                        hopCount = cursor.getInt(cursor.getColumnIndexOrThrow("hop_count")),
                        ttl = cursor.getInt(cursor.getColumnIndexOrThrow("ttl")),
                        payload = cursor.getBlob(cursor.getColumnIndexOrThrow("payload")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return items
    }
    
    fun removeRelayPayload(id: Long) {
        db.delete(DatabaseHelper.TABLE_RELAY_QUEUE, "id = ?", arrayOf(id.toString()))
    }

    // --- Mailbox Queue ---
    fun enqueueMailboxPayload(item: MailboxQueueItem): Long {
        val values = ContentValues().apply {
            put("dest_onion", item.destOnion)
            put("payload", item.payload)
            put("created_at", item.createdAt)
        }
        return db.insert(DatabaseHelper.TABLE_MAILBOX_QUEUE, null, values)
    }

    fun getPendingMailboxPayloads(): List<MailboxQueueItem> {
        val items = mutableListOf<MailboxQueueItem>()
        db.query(DatabaseHelper.TABLE_MAILBOX_QUEUE, null, null, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    MailboxQueueItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        destOnion = cursor.getString(cursor.getColumnIndexOrThrow("dest_onion")),
                        payload = cursor.getBlob(cursor.getColumnIndexOrThrow("payload")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return items
    }
    
    fun removeMailboxPayload(id: Long) {
        db.delete(DatabaseHelper.TABLE_MAILBOX_QUEUE, "id = ?", arrayOf(id.toString()))
    }
    // --- Forums DAO ---
    fun insertForum(forum: Forum): Long {
        val values = ContentValues().apply {
            put("title", forum.title)
            put("description", forum.description)
            put("creator_public_key", forum.creatorPublicKey)
        }
        return db.insert(DatabaseHelper.TABLE_FORUMS, null, values)
    }

    fun getAllForums(): List<Forum> {
        val forums = mutableListOf<Forum>()
        db.query(DatabaseHelper.TABLE_FORUMS, null, null, null, null, null, "id DESC").use { cursor ->
            while (cursor.moveToNext()) {
                forums.add(Forum(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    creatorPublicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("creator_public_key"))
                ))
            }
        }
        return forums
    }

    fun insertForumPost(post: ForumPost): Long {
        val values = ContentValues().apply {
            put("forum_id", post.forumId)
            put("author_public_key", post.authorPublicKey)
            put("body", post.body)
            put("timestamp", post.timestamp)
        }
        return db.insert(DatabaseHelper.TABLE_FORUM_POSTS, null, values)
    }

    fun getForumPosts(forumId: Long): List<ForumPost> {
        val posts = mutableListOf<ForumPost>()
        db.query(DatabaseHelper.TABLE_FORUM_POSTS, null, "forum_id = ?", arrayOf(forumId.toString()), null, null, "timestamp ASC").use { cursor ->
            while (cursor.moveToNext()) {
                posts.add(ForumPost(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    forumId = cursor.getLong(cursor.getColumnIndexOrThrow("forum_id")),
                    authorPublicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("author_public_key")),
                    body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return posts
    }

    // --- Blogs DAO ---
    fun insertBlog(blog: Blog): Long {
        val values = ContentValues().apply {
            put("author_public_key", blog.authorPublicKey)
            put("title", blog.title)
        }
        return db.insert(DatabaseHelper.TABLE_BLOGS, null, values)
    }

    fun getAllBlogs(): List<Blog> {
        val blogs = mutableListOf<Blog>()
        db.query(DatabaseHelper.TABLE_BLOGS, null, null, null, null, null, "title ASC").use { cursor ->
            while (cursor.moveToNext()) {
                blogs.add(Blog(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    authorPublicKey = cursor.getBlob(cursor.getColumnIndexOrThrow("author_public_key")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                ))
            }
        }
        return blogs
    }

    fun insertBlogPost(post: BlogPost): Long {
        val values = ContentValues().apply {
            put("blog_id", post.blogId)
            put("title", post.title)
            put("body", post.body)
            put("timestamp", post.timestamp)
        }
        return db.insert(DatabaseHelper.TABLE_BLOG_POSTS, null, values)
    }

    fun getBlogPosts(blogId: Long): List<BlogPost> {
        val posts = mutableListOf<BlogPost>()
        db.query(DatabaseHelper.TABLE_BLOG_POSTS, null, "blog_id = ?", arrayOf(blogId.toString()), null, null, "timestamp DESC").use { cursor ->
            while (cursor.moveToNext()) {
                posts.add(BlogPost(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    blogId = cursor.getLong(cursor.getColumnIndexOrThrow("blog_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return posts
    }
}

data class RelayQueueItem(
    val id: Long = 0,
    val destOnion: String,
    val hopCount: Int,
    val ttl: Int,
    val payload: ByteArray,
    val createdAt: Long = System.currentTimeMillis()
)

data class MailboxQueueItem(
    val id: Long = 0,
    val destOnion: String,
    val payload: ByteArray,
    val createdAt: Long = System.currentTimeMillis()
)

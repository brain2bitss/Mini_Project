package com.miniproject.core.db

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

class DatabaseHelper(context: Context, private val passphrase: ByteArray) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        SQLiteDatabase.loadLibs(context)
    }

    fun openDatabase(): SQLiteDatabase {
        return getWritableDatabase(passphrase)
    }

    companion object {
        private const val DATABASE_NAME = "bramble.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_IDENTITY = "identity"
        const val TABLE_CONTACTS = "contacts"
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_MESSAGES = "messages"
        const val TABLE_FORUMS = "forums"
        const val TABLE_FORUM_POSTS = "forum_posts"
        const val TABLE_BLOGS = "blogs"
        const val TABLE_BLOG_POSTS = "blog_posts"
        const val TABLE_OUTGOING_QUEUE = "outgoing_queue"
        const val TABLE_RELAY_QUEUE = "relay_queue"
        const val TABLE_MAILBOX_QUEUE = "mailbox_queue"
        const val TABLE_TRANSPORTS = "transports"

        // Schema Creation Statements
        private const val CREATE_TABLE_IDENTITY = """
            CREATE TABLE $TABLE_IDENTITY (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nickname TEXT NOT NULL,
                pubkey BLOB NOT NULL,
                privkey_encrypted BLOB NOT NULL,
                onion_address TEXT,
                created_at INTEGER NOT NULL
            );
        """

        private const val CREATE_TABLE_CONTACTS = """
            CREATE TABLE $TABLE_CONTACTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                alias TEXT NOT NULL,
                pubkey BLOB NOT NULL UNIQUE,
                onion_address TEXT,
                relay_enabled INTEGER DEFAULT 0,
                mailbox_address TEXT,
                last_seen_at INTEGER,
                created_at INTEGER NOT NULL
            );
        """

        private const val CREATE_TABLE_SESSIONS = """
            CREATE TABLE $TABLE_SESSIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER NOT NULL,
                session_id BLOB NOT NULL,
                send_chain_key BLOB NOT NULL,
                receive_chain_key BLOB NOT NULL,
                root_key BLOB NOT NULL,
                send_count INTEGER DEFAULT 0,
                receive_count INTEGER DEFAULT 0,
                opened_at INTEGER NOT NULL,
                FOREIGN KEY (contact_id) REFERENCES $TABLE_CONTACTS(id) ON DELETE CASCADE
            );
        """

        private const val CREATE_TABLE_MESSAGES = """
            CREATE TABLE $TABLE_MESSAGES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER,
                forum_thread_id INTEGER,
                direction INTEGER NOT NULL,
                state INTEGER NOT NULL,
                body_encrypted BLOB NOT NULL,
                timestamp INTEGER NOT NULL,
                delivered_at INTEGER,
                read_at INTEGER,
                expiry_at INTEGER,
                FOREIGN KEY(contact_id) REFERENCES $TABLE_CONTACTS(id) ON DELETE CASCADE
            );
        """

        private const val CREATE_TABLE_FORUMS = """
            CREATE TABLE $TABLE_FORUMS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT,
                creator_public_key BLOB NOT NULL
            );
        """

        private const val CREATE_TABLE_FORUM_POSTS = """
            CREATE TABLE $TABLE_FORUM_POSTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                forum_id INTEGER NOT NULL,
                author_public_key BLOB NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(forum_id) REFERENCES $TABLE_FORUMS(id) ON DELETE CASCADE
            );
        """

        private const val CREATE_TABLE_BLOGS = """
            CREATE TABLE $TABLE_BLOGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                author_public_key BLOB NOT NULL UNIQUE,
                title TEXT NOT NULL
            );
        """

        private const val CREATE_TABLE_BLOG_POSTS = """
            CREATE TABLE $TABLE_BLOG_POSTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                blog_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(blog_id) REFERENCES $TABLE_BLOGS(id) ON DELETE CASCADE
            );
        """

        private const val CREATE_TABLE_OUTGOING_QUEUE = """
            CREATE TABLE $TABLE_OUTGOING_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER,
                payload BLOB NOT NULL,
                state INTEGER NOT NULL,
                attempts INTEGER DEFAULT 0,
                next_attempt_at INTEGER,
                created_at INTEGER NOT NULL
            );
        """

        private const val CREATE_TABLE_RELAY_QUEUE = """
            CREATE TABLE $TABLE_RELAY_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dest_onion TEXT NOT NULL,
                hop_count INTEGER NOT NULL,
                ttl INTEGER NOT NULL,
                payload BLOB NOT NULL,
                created_at INTEGER NOT NULL
            );
        """

        private const val CREATE_TABLE_MAILBOX_QUEUE = """
            CREATE TABLE $TABLE_MAILBOX_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dest_onion TEXT NOT NULL,
                payload BLOB NOT NULL,
                created_at INTEGER NOT NULL
            );
        """

        private const val CREATE_TABLE_TRANSPORTS = """
            CREATE TABLE $TABLE_TRANSPORTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                last_connected_at INTEGER,
                last_error TEXT,
                FOREIGN KEY(contact_id) REFERENCES $TABLE_CONTACTS(id) ON DELETE CASCADE
            );
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_IDENTITY)
        db.execSQL(CREATE_TABLE_CONTACTS)
        db.execSQL(CREATE_TABLE_SESSIONS)
        db.execSQL(CREATE_TABLE_MESSAGES)
        db.execSQL(CREATE_TABLE_FORUMS)
        db.execSQL(CREATE_TABLE_FORUM_POSTS)
        db.execSQL(CREATE_TABLE_BLOGS)
        db.execSQL(CREATE_TABLE_BLOG_POSTS)
        db.execSQL(CREATE_TABLE_OUTGOING_QUEUE)
        db.execSQL(CREATE_TABLE_RELAY_QUEUE)
        db.execSQL(CREATE_TABLE_MAILBOX_QUEUE)
        db.execSQL(CREATE_TABLE_TRANSPORTS)
        
        // Create indexes
        db.execSQL("CREATE INDEX idx_messages_contact_ts ON $TABLE_MESSAGES(contact_id, timestamp DESC)")
        db.execSQL("CREATE INDEX idx_messages_expiry ON $TABLE_MESSAGES(expiry_at) WHERE expiry_at IS NOT NULL")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRANSPORTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MAILBOX_QUEUE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RELAY_QUEUE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_OUTGOING_QUEUE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOG_POSTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FORUM_POSTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FORUMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_IDENTITY")
        onCreate(db)
    }
}

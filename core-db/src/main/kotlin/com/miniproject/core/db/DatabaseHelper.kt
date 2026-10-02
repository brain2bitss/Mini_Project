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

        // Table Names
        const val TABLE_CONTACTS = "contacts"
        const val TABLE_MESSAGES = "messages"
        const val TABLE_FORUMS = "forums"
        const val TABLE_FORUM_POSTS = "forum_posts"
        const val TABLE_BLOGS = "blogs"
        const val TABLE_BLOG_POSTS = "blog_posts"
        const val TABLE_TRANSPORT_QUEUE = "transport_queue"

        // Schema Creation Statements
        private const val CREATE_TABLE_CONTACTS = """
            CREATE TABLE $TABLE_CONTACTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                alias TEXT NOT NULL,
                public_key BLOB NOT NULL UNIQUE,
                onion_address TEXT,
                status INTEGER DEFAULT 0
            );
        """

        private const val CREATE_TABLE_MESSAGES = """
            CREATE TABLE $TABLE_MESSAGES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_sent INTEGER NOT NULL,
                is_read INTEGER NOT NULL,
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

        private const val CREATE_TABLE_TRANSPORT_QUEUE = """
            CREATE TABLE $TABLE_TRANSPORT_QUEUE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                destination_onion TEXT NOT NULL,
                payload BLOB NOT NULL,
                priority INTEGER DEFAULT 0,
                timestamp INTEGER NOT NULL
            );
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE_CONTACTS)
        db.execSQL(CREATE_TABLE_MESSAGES)
        db.execSQL(CREATE_TABLE_FORUMS)
        db.execSQL(CREATE_TABLE_FORUM_POSTS)
        db.execSQL(CREATE_TABLE_BLOGS)
        db.execSQL(CREATE_TABLE_BLOG_POSTS)
        db.execSQL(CREATE_TABLE_TRANSPORT_QUEUE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle database schema upgrades here
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRANSPORT_QUEUE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOG_POSTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BLOGS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FORUM_POSTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FORUMS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        onCreate(db)
    }
}

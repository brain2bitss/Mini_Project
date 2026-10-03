package com.miniproject.ui

import android.app.Application
import com.miniproject.core.db.Daos
import com.miniproject.core.db.DatabaseHelper
import com.miniproject.core.db.MessageRepository
import com.miniproject.transport.tor.TorManager
import com.miniproject.transport.tor.QueueWorker
import com.miniproject.transport.tor.btp.BtpSessionManager

class BrambleApp : Application() {

    lateinit var daos: Daos
    lateinit var messageRepository: MessageRepository
    lateinit var torManager: TorManager
    lateinit var meshManager: com.miniproject.transport.mesh.MeshManager
    lateinit var queueWorker: QueueWorker
    lateinit var btpSessionManager: BtpSessionManager

    private var isUnlocked = false
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate() {
        super.onCreate()
        // Initialize Managers in background
        torManager = TorManager(this)
        torManager.start()
        
        meshManager = com.miniproject.transport.mesh.MeshManager(this)
    }

    /**
     * Unlocks the database with the provided passphrase.
     * Initializes DAOs, repositories, and queue workers.
     */
    fun unlock(passphrase: String) {
        if (isUnlocked) return
        
        dbHelper = DatabaseHelper(this, passphrase.toByteArray())
        val db = dbHelper.openDatabase()
        daos = Daos(db)
        
        // Derive a 32-byte key for MessageRepository AES-256-GCM.
        // In a real app we'd use Argon2id, but for now we'll hash the passphrase using Blake2b
        val dbEncryptionKey = com.miniproject.core.crypto.CryptoManager.hashBlake2b(passphrase.toByteArray(), 32)
        messageRepository = MessageRepository(daos, dbEncryptionKey)
        
        btpSessionManager = BtpSessionManager(daos)
        queueWorker = QueueWorker(daos, torManager)
        
        queueWorker.start()
        isUnlocked = true
    }
}

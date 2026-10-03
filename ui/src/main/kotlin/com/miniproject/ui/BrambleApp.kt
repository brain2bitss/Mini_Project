package com.miniproject.ui

import android.app.Application
import com.miniproject.core.db.Daos
import com.miniproject.core.db.DatabaseHelper
import com.miniproject.core.db.MessageRepository
import com.miniproject.transport.tor.TorManager
import com.miniproject.transport.tor.QueueWorker
import com.miniproject.transport.tor.btp.BtpSessionManager
import kotlinx.coroutines.launch

class BrambleApp : Application() {

    lateinit var daos: Daos
    lateinit var messageRepository: MessageRepository
    lateinit var torManager: TorManager
    lateinit var mailboxManager: com.miniproject.mailbox.MailboxManager
    lateinit var meshManager: com.miniproject.transport.mesh.MeshManager
    lateinit var queueWorker: QueueWorker
    lateinit var btpSessionManager: BtpSessionManager

    lateinit var relayProtocol: com.miniproject.transport.relay.RelayProtocol
        private set

    private var isUnlocked = false
    private lateinit var dbHelper: DatabaseHelper

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

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
        val dbEncryptionKey = com.miniproject.core.crypto.CryptoManager.hashBlake2b(passphrase.toByteArray(), 32)
        messageRepository = MessageRepository(daos, dbEncryptionKey)
        
        btpSessionManager = BtpSessionManager(daos)
        queueWorker = QueueWorker(daos, torManager)
        mailboxManager = com.miniproject.mailbox.MailboxManager(torManager, daos)
        
        relayProtocol = com.miniproject.transport.relay.RelayProtocol(daos)
        
        // Start listening for Mesh (Bluetooth) connections and sync relays
        meshManager.startBluetoothListening { socket ->
            appScope.launch {
                try {
                    relayProtocol.syncRelays(socket.inputStream, socket.outputStream)
                } catch (e: Exception) {
                    // ignore sync errors
                } finally {
                    socket.close()
                }
            }
        }
        
        // Start listening for Tor connections
        torManager.listenForPeers { socket ->
            appScope.launch {
                try {
                    // Quick peek to see if it's a Mailbox sync or chat connection
                    // For MVP simplicity, we just try to read an integer. If it's a mailbox CMD, MailboxManager handles it.
                    // But an InputStream can't be easily peeked without PushbackInputStream.
                    // So we will just let MailboxManager handle it if mailbox mode is enabled.
                    // If not, we'd normally route it to BtpSessionManager.
                    if (mailboxManager.isMailboxModeEnabled) {
                        mailboxManager.handleIncomingConnection(socket)
                    } else {
                        // TODO: handle incoming BTP connection for chat
                        socket.close()
                    }
                } catch (e: Exception) {
                    socket.close()
                }
            }
        }
        
        queueWorker.start()
        isUnlocked = true
    }
}

package com.miniproject.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miniproject.core.db.Contact
import com.miniproject.core.db.DecryptedMessage
import com.miniproject.core.db.MessageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrambleApp

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _myOnionAddress = MutableStateFlow<String?>(null)
    val myOnionAddress: StateFlow<String?> = _myOnionAddress

    private val _isMailboxMode = MutableStateFlow(false)
    val isMailboxMode: StateFlow<Boolean> = _isMailboxMode

    fun toggleMailboxMode() {
        _isMailboxMode.value = !_isMailboxMode.value
        if (_isMailboxMode.value) {
            app.mailboxManager.enableMailboxMode()
        }
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _contacts.value = app.daos.getAllContacts()
            _myOnionAddress.value = app.torManager.onionAddress
        }
    }

    fun addContact(onionAddress: String, nickname: String = "Unknown") {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanOnion = onionAddress.replace("bramble://", "").replace(".onion", "")
            val newContact = Contact(
                alias = nickname,
                onionAddress = cleanOnion,
                pubkey = ByteArray(32) // Placeholder for actual key exchange
            )
            app.daos.insertContact(newContact)
            loadContacts()
        }
    }
    private var bluetoothReceiver: android.content.BroadcastReceiver? = null

    fun startBluetoothScan(onDeviceFound: (String, String) -> Unit) {
        bluetoothReceiver = app.meshManager.startDiscovery(onDeviceFound)
    }

    fun stopBluetoothScan() {
        app.meshManager.stopDiscovery(bluetoothReceiver)
        bluetoothReceiver = null
    }

    fun connectAndSyncRelays(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val socket = app.meshManager.connectToBluetoothPeer(address)
            if (socket != null) {
                try {
                    app.relayProtocol.syncRelays(socket.inputStream, socket.outputStream)
                } catch (e: Exception) {
                    // ignore
                } finally {
                    socket.close()
                }
            }
        }
    }
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrambleApp

    private val _messages = MutableStateFlow<List<DecryptedMessage>>(emptyList())
    val messages: StateFlow<List<DecryptedMessage>> = _messages

    private val _contact = MutableStateFlow<Contact?>(null)
    val contact: StateFlow<Contact?> = _contact

    fun loadChat(contactId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val allContacts = app.daos.getAllContacts()
            _contact.value = allContacts.find { it.id == contactId }
            _messages.value = app.messageRepository.getMessagesForContact(contactId)
        }
    }

    fun sendMessage(contactId: Long, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Save to DB via MessageRepository
            val msgId = app.messageRepository.saveMessage(
                contactId = contactId,
                direction = 1, // OUT
                state = MessageState.QUEUED,
                bodyPlaintext = body
            )
            
            // Re-load messages to update UI
            _messages.value = app.messageRepository.getMessagesForContact(contactId)
            
            // TODO: Extract keys via BtpSessionManager, encrypt into payload, and queue
            // For now, just a placeholder that adds it to the outgoing queue
            app.daos.enqueuePayload(
                com.miniproject.core.db.OutgoingQueueItem(
                    contactId = contactId,
                    payload = body.toByteArray(), // Usually encrypted BTP payload
                    attempts = 0,
                    nextAttemptAt = System.currentTimeMillis()
                )
            )
            
            // Trigger the worker to process the queue immediately
            app.queueWorker.trigger()
        }
    }
}

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrambleApp

    fun unlockApp(passphrase: String) {
        app.unlock(passphrase)
    }
}

class ForumsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrambleApp

    private val _forums = MutableStateFlow<List<com.miniproject.core.db.Forum>>(emptyList())
    val forums: StateFlow<List<com.miniproject.core.db.Forum>> = _forums

    private val _posts = MutableStateFlow<List<com.miniproject.core.db.ForumPost>>(emptyList())
    val posts: StateFlow<List<com.miniproject.core.db.ForumPost>> = _posts

    fun loadForums() {
        viewModelScope.launch(Dispatchers.IO) {
            _forums.value = app.daos.getAllForums()
        }
    }

    fun loadPosts(forumId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _posts.value = app.daos.getForumPosts(forumId)
        }
    }
    
    fun createForum(title: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pubkey = app.daos.getIdentity()?.pubkey ?: ByteArray(32)
            app.daos.insertForum(com.miniproject.core.db.Forum(title = title, description = description, creatorPublicKey = pubkey))
            loadForums()
        }
    }

    fun postReply(forumId: Long, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pubkey = app.daos.getIdentity()?.pubkey ?: ByteArray(32)
            app.daos.insertForumPost(com.miniproject.core.db.ForumPost(forumId = forumId, authorPublicKey = pubkey, body = body))
            loadPosts(forumId)
        }
    }
}

class BlogsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BrambleApp

    private val _blogs = MutableStateFlow<List<com.miniproject.core.db.Blog>>(emptyList())
    val blogs: StateFlow<List<com.miniproject.core.db.Blog>> = _blogs

    private val _posts = MutableStateFlow<List<com.miniproject.core.db.BlogPost>>(emptyList())
    val posts: StateFlow<List<com.miniproject.core.db.BlogPost>> = _posts

    fun loadBlogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _blogs.value = app.daos.getAllBlogs()
        }
    }

    fun loadPosts(blogId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _posts.value = app.daos.getBlogPosts(blogId)
        }
    }
}

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

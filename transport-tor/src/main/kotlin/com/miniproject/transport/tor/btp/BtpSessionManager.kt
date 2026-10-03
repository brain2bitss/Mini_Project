package com.miniproject.transport.tor.btp

import com.miniproject.core.db.Contact
import com.miniproject.core.db.Daos
import com.miniproject.core.db.Session
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages BTP sessions with peers.
 * Handles creating new sessions via BtpHandshake, storing session state (DoubleRatchet keys)
 * in the database, resuming sessions from the database, and providing access to
 * the active DoubleRatchet instance for each peer.
 */
class BtpSessionManager(private val daos: Daos) {

    // In-memory cache of active DoubleRatchet instances, keyed by contact ID
    private val activeRatchets = ConcurrentHashMap<Long, DoubleRatchet>()

    /**
     * Initializes a new session with a peer as the initiator (Alice).
     * Typically called after a successful BTP handshake.
     *
     * @param contact The peer we are connecting to
     * @param sharedSecret The transcript hash from the BTP handshake (32 bytes)
     * @param responderDhPubKey The responder's DH public key (32 bytes)
     * @return The initialized DoubleRatchet
     */
    fun initSessionAsInitiator(
        contact: Contact,
        sharedSecret: ByteArray,
        responderDhPubKey: ByteArray
    ): DoubleRatchet {
        val ratchet = DoubleRatchet.initAsInitiator(sharedSecret, responderDhPubKey)
        activeRatchets[contact.id] = ratchet
        
        // TODO: Save initial state to DB via daos.insertSession once DoubleRatchet exposes keys
        
        return ratchet
    }

    /**
     * Initializes a new session with a peer as the responder (Bob).
     *
     * @param contact The peer connecting to us
     * @param sharedSecret The transcript hash from the BTP handshake (32 bytes)
     * @param ourDhKeyPair Our DH keypair used for the ratchet
     * @return The initialized DoubleRatchet
     */
    fun initSessionAsResponder(
        contact: Contact,
        sharedSecret: ByteArray,
        ourDhKeyPair: org.bouncycastle.crypto.AsymmetricCipherKeyPair
    ): DoubleRatchet {
        val ratchet = DoubleRatchet.initAsResponder(sharedSecret, ourDhKeyPair)
        activeRatchets[contact.id] = ratchet
        
        // TODO: Save initial state to DB
        
        return ratchet
    }

    /**
     * Returns the active DoubleRatchet for a contact, if any.
     */
    fun getActiveRatchet(contactId: Long): DoubleRatchet? {
        return activeRatchets[contactId]
    }

    /**
     * Loads a session from the database and initializes a DoubleRatchet from it.
     */
    fun resumeSession(contactId: Long): DoubleRatchet? {
        val sessionEntity = daos.getSessionForContact(contactId) ?: return null
        
        // TODO: We need a way to instantiate DoubleRatchet from existing state.
        // Currently DoubleRatchet only has initAsInitiator and initAsResponder which
        // do fresh DH ratchets. We need a way to restore from saved keys.
        
        return null
    }

    /**
     * Saves the current state of a DoubleRatchet to the database.
     */
    fun saveSessionState(contactId: Long, sessionId: ByteArray) {
        val ratchet = activeRatchets[contactId] ?: return
        
        // TODO: Extract keys from ratchet and call daos.updateSessionKeys
    }
    
    fun removeActiveRatchet(contactId: Long) {
        activeRatchets.remove(contactId)
    }
}

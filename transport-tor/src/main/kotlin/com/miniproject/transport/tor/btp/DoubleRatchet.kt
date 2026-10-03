package com.miniproject.transport.tor.btp

import com.miniproject.core.crypto.CryptoManager
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.nio.ByteBuffer

/**
 * Per-message header included with every ratchet-encrypted message.
 * Contains the sender's current DH ratchet public key and message counters
 * so the receiver can determine whether a DH ratchet step is needed.
 *
 * Wire format (40 bytes):
 *   [32 bytes: dhRatchetPubKey]
 *   [4 bytes: previousChainLength (big-endian int)]
 *   [4 bytes: messageNumber (big-endian int)]
 */
data class RatchetHeader(
    val dhRatchetPubKey: ByteArray,   // 32 bytes — sender's current DH public key
    val previousChainLength: Int,      // msgs sent under previous DH ratchet
    val messageNumber: Int             // msg number under current DH ratchet
) {
    companion object {
        const val SERIALIZED_SIZE = 32 + 4 + 4  // 40 bytes

        fun deserialize(bytes: ByteArray): RatchetHeader {
            require(bytes.size >= SERIALIZED_SIZE) { "Invalid header size: ${bytes.size}" }
            val buf = ByteBuffer.wrap(bytes)
            val dhPub = ByteArray(32)
            buf.get(dhPub)
            val prevChainLen = buf.int
            val msgNum = buf.int
            return RatchetHeader(dhPub, prevChainLen, msgNum)
        }
    }

    fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(SERIALIZED_SIZE)
        buf.put(dhRatchetPubKey)
        buf.putInt(previousChainLength)
        buf.putInt(messageNumber)
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetHeader) return false
        return dhRatchetPubKey.contentEquals(other.dhRatchetPubKey) &&
               previousChainLength == other.previousChainLength &&
               messageNumber == other.messageNumber
    }

    override fun hashCode(): Int {
        var result = dhRatchetPubKey.contentHashCode()
        result = 31 * result + previousChainLength
        result = 31 * result + messageNumber
        return result
    }
}

/**
 * Encrypted message produced by the Double Ratchet.
 *
 * Wire format:
 *   [40 bytes: RatchetHeader]
 *   [N bytes: AES-256-GCM ciphertext (12 IV + encrypted + 16 tag)]
 */
data class RatchetMessage(
    val header: RatchetHeader,
    val ciphertext: ByteArray
) {
    companion object {
        fun deserialize(bytes: ByteArray): RatchetMessage {
            require(bytes.size > RatchetHeader.SERIALIZED_SIZE) {
                "RatchetMessage too short: ${bytes.size}"
            }
            val headerBytes = bytes.copyOfRange(0, RatchetHeader.SERIALIZED_SIZE)
            val ciphertext = bytes.copyOfRange(RatchetHeader.SERIALIZED_SIZE, bytes.size)
            return RatchetMessage(RatchetHeader.deserialize(headerBytes), ciphertext)
        }
    }

    fun serialize(): ByteArray {
        return header.serialize() + ciphertext
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetMessage) return false
        return header == other.header && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * header.hashCode() + ciphertext.contentHashCode()
}

/**
 * Signal-style Double Ratchet providing:
 *   - Per-message forward secrecy via symmetric chain ratchet (KDF_CK)
 *   - Post-compromise security via DH ratchet steps (KDF_RK + X25519)
 *   - Out-of-order message decryption via skipped message key cache
 *
 * ## Initialization
 *
 * After a BTP handshake, the two sides initialize as:
 *
 *   Alice (initiator):
 *     DoubleRatchet.initAsInitiator(sharedSecret, bobDhPubKey)
 *     — Performs the first DH ratchet step immediately, can send right away.
 *
 *   Bob (responder):
 *     DoubleRatchet.initAsResponder(sharedSecret, bobDhKeyPair)
 *     — Waits for Alice's first message to learn her DH pub and derive chains.
 *
 * ## Message flow
 *
 *   encrypt(plaintext) → RatchetMessage  (advances send chain)
 *   decrypt(message)   → ByteArray       (advances receive chain, may trigger DH ratchet)
 *
 * ## DH Ratchet
 *
 * A DH ratchet step occurs automatically whenever a message arrives with
 * a previously unseen DH public key in its header. This ensures that
 * each "turn" of conversation uses fresh DH key material.
 */
class DoubleRatchet private constructor(
    private var rootKey: ByteArray,
    private var sendChainKey: ByteArray?,
    private var receiveChainKey: ByteArray?,
    private var sendDhKeyPair: AsymmetricCipherKeyPair,
    private var receiveDhPubKey: ByteArray?,
    private var sendMessageNum: Int = 0,
    private var receiveMessageNum: Int = 0,
    private var previousSendChainLength: Int = 0,
    private val skippedKeys: MutableMap<String, ByteArray> = mutableMapOf(),
    private val maxSkip: Int = MAX_SKIP_DEFAULT
) {

    companion object {
        const val MAX_SKIP_DEFAULT = 256

        /**
         * Initialize the Double Ratchet as the initiator (Alice).
         *
         * Alice performs the first DH ratchet step using the shared secret
         * and Bob's DH public key. She can encrypt immediately after init.
         *
         * @param sharedSecret 32-byte shared secret (e.g., transcriptHash from handshake)
         * @param peerDhPubKey 32-byte X25519 public key of the responder
         * @return initialized DoubleRatchet ready to encrypt
         */
        fun initAsInitiator(
            sharedSecret: ByteArray,
            peerDhPubKey: ByteArray
        ): DoubleRatchet {
            require(sharedSecret.size == 32) { "sharedSecret must be 32 bytes" }
            require(peerDhPubKey.size == 32) { "peerDhPubKey must be 32 bytes" }

            // Generate our ratchet DH keypair
            val dhPair = CryptoManager.generateX25519KeyPair()

            // Perform DH and derive send chain
            val dhOutput = CryptoManager.performX25519KeyAgreement(
                dhPair.private as X25519PrivateKeyParameters,
                X25519PublicKeyParameters(peerDhPubKey, 0)
            )
            val (rootKey, sendChainKey) = RatchetKdf.kdfRootKey(sharedSecret, dhOutput)

            return DoubleRatchet(
                rootKey = rootKey,
                sendChainKey = sendChainKey,
                receiveChainKey = null,       // derived on first receive
                sendDhKeyPair = dhPair,
                receiveDhPubKey = peerDhPubKey,
                sendMessageNum = 0,
                receiveMessageNum = 0,
                previousSendChainLength = 0
            )
        }

        /**
         * Initialize the Double Ratchet as the responder (Bob).
         *
         * Bob waits for Alice's first message to learn her ratchet DH pub key.
         * The DH ratchet step occurs on the first call to decrypt().
         *
         * @param sharedSecret 32-byte shared secret (same as Alice's)
         * @param ourDhKeyPair Bob's X25519 keypair (the one whose pub was sent to Alice)
         * @return initialized DoubleRatchet ready to receive
         */
        fun initAsResponder(
            sharedSecret: ByteArray,
            ourDhKeyPair: AsymmetricCipherKeyPair
        ): DoubleRatchet {
            require(sharedSecret.size == 32) { "sharedSecret must be 32 bytes" }

            return DoubleRatchet(
                rootKey = sharedSecret.copyOf(),
                sendChainKey = null,           // derived on first DH ratchet
                receiveChainKey = null,         // derived on first DH ratchet
                sendDhKeyPair = ourDhKeyPair,
                receiveDhPubKey = null          // learned from Alice's first message
            )
        }
    }

    /**
     * Returns the current DH ratchet public key (32 bytes).
     */
    fun getDhPubKey(): ByteArray =
        (sendDhKeyPair.public as X25519PublicKeyParameters).encoded

    /**
     * Returns the number of messages sent under the current send chain.
     */
    fun getSendMessageNum(): Int = sendMessageNum

    /**
     * Returns the number of cached skipped message keys.
     */
    fun skippedKeyCount(): Int = skippedKeys.size

    /**
     * Encrypt a plaintext message.
     *
     * Advances the send chain ratchet and derives a unique per-message key.
     * The header (containing our DH pub key) is authenticated as AAD.
     *
     * @param plaintext the message to encrypt
     * @return RatchetMessage ready for transmission
     * @throws IllegalStateException if the send chain is not yet initialized
     */
    @Synchronized
    fun encrypt(plaintext: ByteArray): RatchetMessage {
        requireNotNull(sendChainKey) {
            "Send chain not initialized — responder must receive first"
        }

        val (newChainKey, messageKey) = RatchetKdf.kdfChainKey(sendChainKey!!)
        sendChainKey = newChainKey

        val header = RatchetHeader(
            dhRatchetPubKey = getDhPubKey(),
            previousChainLength = previousSendChainLength,
            messageNumber = sendMessageNum
        )
        sendMessageNum++

        // Authenticate the header as AAD — prevents header tampering
        val headerBytes = header.serialize()
        val ciphertext = CryptoManager.encryptAesGcm(messageKey, plaintext, headerBytes)

        // Zeroize message key — it must never be reused
        messageKey.fill(0)

        return RatchetMessage(header, ciphertext)
    }

    /**
     * Decrypt a received ratchet message.
     *
     * Handles three cases:
     *   1. Message from a previously skipped position — uses cached key
     *   2. Message with a new DH public key — performs DH ratchet step
     *   3. Message in the current receive chain — advances chain
     *
     * @param message the received RatchetMessage
     * @return decrypted plaintext
     * @throws SecurityException if decryption fails (tampered or wrong key)
     */
    @Synchronized
    fun decrypt(message: RatchetMessage): ByteArray {
        // 1. Try skipped message keys first
        val skippedResult = trySkippedKeys(message)
        if (skippedResult != null) return skippedResult

        val headerPub = message.header.dhRatchetPubKey

        // 2. If the DH pub key is new, perform a DH ratchet step
        if (receiveDhPubKey == null || !headerPub.contentEquals(receiveDhPubKey!!)) {
            // Cache keys for any missed messages in the current receive chain
            if (receiveChainKey != null) {
                skipMessages(message.header.previousChainLength)
            }
            // Perform DH ratchet step with the new peer DH pub key
            dhRatchetStep(headerPub)
        }

        // 3. Skip any missed messages in the (possibly new) receive chain
        skipMessages(message.header.messageNumber)

        // 4. Derive the message key for this message
        requireNotNull(receiveChainKey) { "Receive chain key is null after ratchet step" }
        val (newChainKey, messageKey) = RatchetKdf.kdfChainKey(receiveChainKey!!)
        receiveChainKey = newChainKey
        receiveMessageNum++

        // 5. Decrypt with header as AAD
        val headerBytes = message.header.serialize()
        try {
            val plaintext = CryptoManager.decryptAesGcm(messageKey, message.ciphertext, headerBytes)
            messageKey.fill(0)
            return plaintext
        } catch (e: Exception) {
            messageKey.fill(0)
            throw SecurityException("Ratchet decryption failed: ${e.message}", e)
        }
    }

    /**
     * Performs a DH ratchet step when a new peer DH public key is received.
     *
     * 1. DH(ourCurrentDH, theirNewDH) → derive new receiveChainKey
     * 2. Generate fresh DH keypair
     * 3. DH(ourNewDH, theirNewDH) → derive new sendChainKey
     *
     * After this step, old DH private keys are discarded.
     */
    private fun dhRatchetStep(newPeerDhPub: ByteArray) {
        previousSendChainLength = sendMessageNum
        sendMessageNum = 0
        receiveMessageNum = 0
        receiveDhPubKey = newPeerDhPub.copyOf()

        // Step 1: Derive new receive chain from DH(currentPriv, newPeerPub)
        val dhOutput1 = CryptoManager.performX25519KeyAgreement(
            sendDhKeyPair.private as X25519PrivateKeyParameters,
            X25519PublicKeyParameters(newPeerDhPub, 0)
        )
        val (newRootKey1, newReceiveChain) = RatchetKdf.kdfRootKey(rootKey, dhOutput1)
        rootKey = newRootKey1
        receiveChainKey = newReceiveChain

        // Step 2: Generate fresh DH keypair (old private key is GC'd)
        sendDhKeyPair = CryptoManager.generateX25519KeyPair()

        // Step 3: Derive new send chain from DH(newPriv, newPeerPub)
        val dhOutput2 = CryptoManager.performX25519KeyAgreement(
            sendDhKeyPair.private as X25519PrivateKeyParameters,
            X25519PublicKeyParameters(newPeerDhPub, 0)
        )
        val (newRootKey2, newSendChain) = RatchetKdf.kdfRootKey(rootKey, dhOutput2)
        rootKey = newRootKey2
        sendChainKey = newSendChain
    }

    /**
     * Advances the receive chain to skip missed messages, caching their
     * message keys for later out-of-order decryption.
     */
    private fun skipMessages(untilMessageNum: Int) {
        if (receiveChainKey == null) return
        val toSkip = untilMessageNum - receiveMessageNum
        require(toSkip >= 0) { "Cannot skip backward: current=$receiveMessageNum, target=$untilMessageNum" }
        require(toSkip <= maxSkip) { "Too many skipped messages: $toSkip (max $maxSkip)" }

        while (receiveMessageNum < untilMessageNum) {
            val (newChainKey, messageKey) = RatchetKdf.kdfChainKey(receiveChainKey!!)
            receiveChainKey = newChainKey
            val key = skippedKeyId(receiveDhPubKey!!, receiveMessageNum)
            skippedKeys[key] = messageKey
            receiveMessageNum++
        }
    }

    /**
     * Tries to decrypt with a previously cached skipped message key.
     * Returns the plaintext if found, null otherwise.
     * The cached key is removed after use (one-time use).
     */
    private fun trySkippedKeys(message: RatchetMessage): ByteArray? {
        val key = skippedKeyId(message.header.dhRatchetPubKey, message.header.messageNumber)
        val messageKey = skippedKeys.remove(key) ?: return null

        val headerBytes = message.header.serialize()
        try {
            val plaintext = CryptoManager.decryptAesGcm(messageKey, message.ciphertext, headerBytes)
            messageKey.fill(0)
            return plaintext
        } catch (e: Exception) {
            messageKey.fill(0)
            throw SecurityException("Skipped key decryption failed: ${e.message}", e)
        }
    }

    /**
     * Creates a unique identifier for a (DH pub key, message number) pair.
     * Uses the first 16 bytes of the DH pub key for compactness.
     */
    private fun skippedKeyId(dhPub: ByteArray, messageNum: Int): String =
        dhPub.take(16).joinToString("") { "%02x".format(it) } + ":$messageNum"
}

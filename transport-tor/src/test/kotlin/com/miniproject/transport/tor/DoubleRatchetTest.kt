package com.miniproject.transport.tor

import com.miniproject.core.crypto.CryptoManager
import com.miniproject.transport.tor.btp.*
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive tests for the Double Ratchet implementation.
 *
 * Tests cover:
 *   1. KDF determinism and non-collision
 *   2. Basic encrypt/decrypt (Alice → Bob)
 *   3. Chain ratchet — multiple messages use different keys
 *   4. DH ratchet — triggered on conversation turn
 *   5. Bidirectional multi-turn conversation (6 messages, 3 DH ratchets each)
 *   6. Out-of-order message handling via skipped key cache
 *   7. Tamper detection — modified ciphertext or header rejected
 *   8. Forward secrecy proof — each message key is unique
 */
class DoubleRatchetTest {

    /**
     * Creates a matched pair of DoubleRatchets (Alice=initiator, Bob=responder)
     * with a fresh shared secret and Bob's DH keypair.
     */
    private fun createRatchetPair(): Pair<DoubleRatchet, DoubleRatchet> {
        val sharedSecret = CryptoManager.hashBlake2b("test-shared-secret".toByteArray(), 32)
        val bobDhKeyPair = CryptoManager.generateX25519KeyPair()
        val bobDhPubKey = (bobDhKeyPair.public as X25519PublicKeyParameters).encoded

        val alice = DoubleRatchet.initAsInitiator(sharedSecret, bobDhPubKey)
        val bob = DoubleRatchet.initAsResponder(sharedSecret, bobDhKeyPair)
        return Pair(alice, bob)
    }

    // ── Test 1: KDF determinism ──────────────────────────────────────────

    @Test
    fun `KDF_RK produces deterministic 32-byte outputs`() {
        val rootKey = ByteArray(32) { it.toByte() }
        val dhOutput = ByteArray(32) { (it + 32).toByte() }

        val (rk1, ck1) = RatchetKdf.kdfRootKey(rootKey, dhOutput)
        val (rk2, ck2) = RatchetKdf.kdfRootKey(rootKey, dhOutput)

        assertArrayEquals("KDF_RK must be deterministic (rootKey)", rk1, rk2)
        assertArrayEquals("KDF_RK must be deterministic (chainKey)", ck1, ck2)
        assertEquals("Root key must be 32 bytes", 32, rk1.size)
        assertEquals("Chain key must be 32 bytes", 32, ck1.size)
        assertFalse("Root key and chain key must differ", rk1.contentEquals(ck1))
    }

    @Test
    fun `KDF_CK produces unique message keys per step`() {
        var chainKey = ByteArray(32) { it.toByte() }
        val messageKeys = mutableListOf<ByteArray>()

        repeat(5) {
            val (newCk, mk) = RatchetKdf.kdfChainKey(chainKey)
            messageKeys.add(mk)
            chainKey = newCk
        }

        // All message keys must be distinct
        for (i in messageKeys.indices) {
            for (j in i + 1 until messageKeys.size) {
                assertFalse(
                    "Message keys $i and $j must differ",
                    messageKeys[i].contentEquals(messageKeys[j])
                )
            }
        }
    }

    // ── Test 2: Basic encrypt/decrypt ────────────────────────────────────

    @Test
    fun `Alice encrypts, Bob decrypts — single message`() {
        val (alice, bob) = createRatchetPair()
        val plaintext = "Hello from Alice".toByteArray()

        val encrypted = alice.encrypt(plaintext)
        val decrypted = bob.decrypt(encrypted)

        assertArrayEquals("Decrypted must match plaintext", plaintext, decrypted)
    }

    // ── Test 3: Chain ratchet — multiple messages same direction ─────────

    @Test
    fun `Alice sends 5 messages, Bob decrypts all in order`() {
        val (alice, bob) = createRatchetPair()

        for (i in 1..5) {
            val plaintext = "Message $i".toByteArray()
            val encrypted = alice.encrypt(plaintext)

            // Each message should have incrementing message numbers
            assertEquals("Message number should be ${i - 1}", i - 1, encrypted.header.messageNumber)

            val decrypted = bob.decrypt(encrypted)
            assertArrayEquals("Message $i decryption", plaintext, decrypted)
        }
    }

    // ── Test 4: DH ratchet triggered on turn change ─────────────────────

    @Test
    fun `Bob replies after Alice — DH ratchet step occurs`() {
        val (alice, bob) = createRatchetPair()

        // Alice → Bob
        val msg1 = alice.encrypt("Alice msg 1".toByteArray())
        val aliceDhPub1 = msg1.header.dhRatchetPubKey.copyOf()
        bob.decrypt(msg1)

        // Bob → Alice (triggers DH ratchet on Bob — new DH keypair)
        val reply1 = bob.encrypt("Bob reply 1".toByteArray())
        val bobDhPub1 = reply1.header.dhRatchetPubKey.copyOf()

        // Bob's DH pub key in the reply should differ from Alice's
        assertFalse(
            "Bob's DH pub must differ from Alice's",
            aliceDhPub1.contentEquals(bobDhPub1)
        )

        // Alice decrypts Bob's reply (triggers DH ratchet on Alice)
        val decrypted = alice.decrypt(reply1)
        assertArrayEquals("Bob reply 1", "Bob reply 1".toByteArray(), decrypted)

        // Alice sends again — her DH pub should be NEW (different from msg1)
        val msg2 = alice.encrypt("Alice msg 2".toByteArray())
        val aliceDhPub2 = msg2.header.dhRatchetPubKey.copyOf()
        assertFalse(
            "Alice's new DH pub must differ from her original",
            aliceDhPub1.contentEquals(aliceDhPub2)
        )

        val decrypted2 = bob.decrypt(msg2)
        assertArrayEquals("Alice msg 2", "Alice msg 2".toByteArray(), decrypted2)
    }

    // ── Test 5: Full bidirectional conversation with multiple DH ratchets ─

    @Test
    fun `3-turn conversation — 6 messages, multiple DH ratchet steps`() {
        val (alice, bob) = createRatchetPair()
        val dhPubKeys = mutableListOf<String>()

        // Turn 1: Alice → Bob
        val m1 = alice.encrypt("Turn 1: Alice".toByteArray())
        dhPubKeys.add(fingerprint(m1.header.dhRatchetPubKey))
        assertArrayEquals("Turn 1", "Turn 1: Alice".toByteArray(), bob.decrypt(m1))

        // Turn 1: Bob → Alice
        val m2 = bob.encrypt("Turn 1: Bob".toByteArray())
        dhPubKeys.add(fingerprint(m2.header.dhRatchetPubKey))
        assertArrayEquals("Turn 1 reply", "Turn 1: Bob".toByteArray(), alice.decrypt(m2))

        // Turn 2: Alice → Bob
        val m3 = alice.encrypt("Turn 2: Alice".toByteArray())
        dhPubKeys.add(fingerprint(m3.header.dhRatchetPubKey))
        assertArrayEquals("Turn 2", "Turn 2: Alice".toByteArray(), bob.decrypt(m3))

        // Turn 2: Bob → Alice
        val m4 = bob.encrypt("Turn 2: Bob".toByteArray())
        dhPubKeys.add(fingerprint(m4.header.dhRatchetPubKey))
        assertArrayEquals("Turn 2 reply", "Turn 2: Bob".toByteArray(), alice.decrypt(m4))

        // Turn 3: Alice → Bob
        val m5 = alice.encrypt("Turn 3: Alice".toByteArray())
        dhPubKeys.add(fingerprint(m5.header.dhRatchetPubKey))
        assertArrayEquals("Turn 3", "Turn 3: Alice".toByteArray(), bob.decrypt(m5))

        // Turn 3: Bob → Alice
        val m6 = bob.encrypt("Turn 3: Bob".toByteArray())
        dhPubKeys.add(fingerprint(m6.header.dhRatchetPubKey))
        assertArrayEquals("Turn 3 reply", "Turn 3: Bob".toByteArray(), alice.decrypt(m6))

        // Verify DH ratchet steps: each turn changes the DH pub key
        // Alice's keys: m1, m3, m5 should all differ
        assertNotEquals("Alice DH ratchet T1→T2", dhPubKeys[0], dhPubKeys[2])
        assertNotEquals("Alice DH ratchet T2→T3", dhPubKeys[2], dhPubKeys[4])
        // Bob's keys: m2, m4, m6 should all differ
        assertNotEquals("Bob DH ratchet T1→T2", dhPubKeys[1], dhPubKeys[3])
        assertNotEquals("Bob DH ratchet T2→T3", dhPubKeys[3], dhPubKeys[5])
    }

    // ── Test 6: Out-of-order messages ───────────────────────────────────

    @Test
    fun `Out-of-order messages — skipped keys cached and used`() {
        val (alice, bob) = createRatchetPair()

        // Alice sends 3 messages
        val msg0 = alice.encrypt("Message 0".toByteArray())
        val msg1 = alice.encrypt("Message 1".toByteArray())
        val msg2 = alice.encrypt("Message 2".toByteArray())

        // Bob decrypts in reverse order: msg2, msg0, msg1
        // msg2 first → Bob skips msgs 0 and 1, caching their keys
        val dec2 = bob.decrypt(msg2)
        assertArrayEquals("msg2 out-of-order", "Message 2".toByteArray(), dec2)
        assertEquals("2 skipped keys cached", 2, bob.skippedKeyCount())

        // msg0 → uses cached skipped key
        val dec0 = bob.decrypt(msg0)
        assertArrayEquals("msg0 out-of-order", "Message 0".toByteArray(), dec0)
        assertEquals("1 skipped key remaining", 1, bob.skippedKeyCount())

        // msg1 → uses last cached key
        val dec1 = bob.decrypt(msg1)
        assertArrayEquals("msg1 out-of-order", "Message 1".toByteArray(), dec1)
        assertEquals("0 skipped keys", 0, bob.skippedKeyCount())
    }

    // ── Test 7: Tamper detection ────────────────────────────────────────

    @Test
    fun `Tampered ciphertext is rejected`() {
        val (alice, bob) = createRatchetPair()
        val encrypted = alice.encrypt("Secret message".toByteArray())

        // Flip one byte in the ciphertext
        val tampered = RatchetMessage(
            encrypted.header,
            encrypted.ciphertext.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
        )

        try {
            bob.decrypt(tampered)
            fail("Tampered message should throw SecurityException")
        } catch (e: SecurityException) {
            // Expected — AES-GCM tag verification failed
            assertTrue(e.message!!.contains("failed"))
        }
    }

    @Test
    fun `Tampered header is rejected`() {
        val (alice, bob) = createRatchetPair()
        val encrypted = alice.encrypt("Secret message".toByteArray())

        // Modify the message number in the header (AAD mismatch)
        val tamperedHeader = RatchetHeader(
            dhRatchetPubKey = encrypted.header.dhRatchetPubKey,
            previousChainLength = encrypted.header.previousChainLength,
            messageNumber = encrypted.header.messageNumber + 1  // wrong number
        )
        val tampered = RatchetMessage(tamperedHeader, encrypted.ciphertext)

        try {
            bob.decrypt(tampered)
            fail("Tampered header should throw SecurityException")
        } catch (e: SecurityException) {
            // Expected — AAD mismatch causes AES-GCM to fail
            assertTrue(e.message!!.contains("failed"))
        }
    }

    // ── Test 8: Forward secrecy proof ───────────────────────────────────

    @Test
    fun `Each encrypted message has unique ciphertext`() {
        val (alice, bob) = createRatchetPair()

        // Encrypt the SAME plaintext 5 times
        val ciphertexts = (1..5).map { alice.encrypt("Same text".toByteArray()).ciphertext }

        // All ciphertexts must differ (unique IV + unique key per message)
        for (i in ciphertexts.indices) {
            for (j in i + 1 until ciphertexts.size) {
                assertFalse(
                    "Ciphertext $i and $j must differ (unique key per message)",
                    ciphertexts[i].contentEquals(ciphertexts[j])
                )
            }
        }
    }

    // ── Test 9: Header serialization round-trip ─────────────────────────

    @Test
    fun `RatchetHeader and RatchetMessage serialize and deserialize correctly`() {
        val dhPub = ByteArray(32) { (it * 3).toByte() }
        val header = RatchetHeader(dhPub, previousChainLength = 42, messageNumber = 7)

        val serialized = header.serialize()
        assertEquals("Header size", RatchetHeader.SERIALIZED_SIZE, serialized.size)

        val deserialized = RatchetHeader.deserialize(serialized)
        assertArrayEquals("DH pub key roundtrip", dhPub, deserialized.dhRatchetPubKey)
        assertEquals("prevChainLen roundtrip", 42, deserialized.previousChainLength)
        assertEquals("msgNum roundtrip", 7, deserialized.messageNumber)

        // RatchetMessage roundtrip
        val ciphertext = ByteArray(100) { it.toByte() }
        val msg = RatchetMessage(header, ciphertext)
        val msgBytes = msg.serialize()
        val msgDeserialized = RatchetMessage.deserialize(msgBytes)
        assertEquals("Header roundtrip in message", deserialized, msgDeserialized.header)
        assertArrayEquals("Ciphertext roundtrip", ciphertext, msgDeserialized.ciphertext)
    }

    // ── Utility ─────────────────────────────────────────────────────────

    private fun fingerprint(pubKey: ByteArray): String =
        pubKey.take(8).joinToString("") { "%02x".format(it) }
}

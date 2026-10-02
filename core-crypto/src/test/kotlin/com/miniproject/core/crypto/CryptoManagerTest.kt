package com.miniproject.core.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class CryptoManagerTest {

    @Test
    fun testX25519KeyAgreement() {
        val alicePair = CryptoManager.generateX25519KeyPair()
        val bobPair = CryptoManager.generateX25519KeyPair()

        val aliceSecret = CryptoManager.performX25519KeyAgreement(
            alicePair.private as org.bouncycastle.crypto.params.X25519PrivateKeyParameters,
            bobPair.public as org.bouncycastle.crypto.params.X25519PublicKeyParameters
        )

        val bobSecret = CryptoManager.performX25519KeyAgreement(
            bobPair.private as org.bouncycastle.crypto.params.X25519PrivateKeyParameters,
            alicePair.public as org.bouncycastle.crypto.params.X25519PublicKeyParameters
        )

        assertArrayEquals("Shared secrets should match", aliceSecret, bobSecret)
        assertEquals(32, aliceSecret.size)
    }

    @Test
    fun testEd25519SignAndVerify() {
        val keyPair = CryptoManager.generateEd25519KeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        
        val message = "Hello, Bramble!".toByteArray(StandardCharsets.UTF_8)
        val signature = CryptoManager.signEd25519(privateKey, message)
        
        val isValid = CryptoManager.verifyEd25519(publicKey, signature, message)
        assertTrue("Signature should be valid", isValid)
        
        val tamperedMessage = "Hello, bramble!".toByteArray(StandardCharsets.UTF_8)
        val isInvalid = CryptoManager.verifyEd25519(publicKey, signature, tamperedMessage)
        assertFalse("Tampered message signature should be invalid", isInvalid)
    }

    @Test
    fun testAesGcmEncryptDecrypt() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Secret Message Data".toByteArray(StandardCharsets.UTF_8)
        val associatedData = "AAD".toByteArray(StandardCharsets.UTF_8)
        
        val ciphertext = CryptoManager.encryptAesGcm(key, plaintext, associatedData)
        val decrypted = CryptoManager.decryptAesGcm(key, ciphertext, associatedData)
        
        assertArrayEquals("Decrypted text should match original", plaintext, decrypted)
    }

    @Test
    fun testAesGcmTamperFailure() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Secret".toByteArray(StandardCharsets.UTF_8)
        
        val ciphertext = CryptoManager.encryptAesGcm(key, plaintext)
        
        // Tamper with the ciphertext (flip a bit in the last byte which is the MAC)
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1].toInt() xor 1).toByte()
        
        try {
            CryptoManager.decryptAesGcm(key, ciphertext)
            fail("Decryption should fail due to tampered MAC")
        } catch (e: InvalidCipherTextException) {
            // Expected
        }
    }

    @Test
    fun testBlake2bHashing() {
        val data = "Hello, BLAKE2b".toByteArray(StandardCharsets.UTF_8)
        val hash1 = CryptoManager.hashBlake2b(data)
        val hash2 = CryptoManager.hashBlake2b(data)
        
        assertArrayEquals("Hashes of same data should match", hash1, hash2)
        assertEquals(32, hash1.size)
        
        val hash3 = CryptoManager.hashBlake2b(data, digestSize = 64)
        assertEquals(64, hash3.size)
    }
}

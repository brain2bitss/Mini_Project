package com.miniproject.core.crypto

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

object CryptoManager {

    private val secureRandom = SecureRandom()

    // --- Key Generation ---

    fun generateX25519KeyPair(): AsymmetricCipherKeyPair {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(secureRandom))
        return generator.generateKeyPair()
    }

    fun generateEd25519KeyPair(): AsymmetricCipherKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(secureRandom))
        return generator.generateKeyPair()
    }

    // --- Key Agreement ---

    fun performX25519KeyAgreement(privateKey: X25519PrivateKeyParameters, publicKey: X25519PublicKeyParameters): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicKey, secret, 0)
        return secret
    }

    // --- Signatures ---

    fun signEd25519(privateKey: Ed25519PrivateKeyParameters, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verifyEd25519(publicKey: Ed25519PublicKeyParameters, signature: ByteArray, message: ByteArray): Boolean {
        val verifier = Ed25519Signer()
        verifier.init(false, publicKey)
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    // --- AES-256-GCM ---

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns concatenated IV + Ciphertext + Auth Tag.
     */
    fun encryptAesGcm(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)

        val cipher = GCMBlockCipher(AESEngine())
        val parameters = AEADParameters(KeyParameter(key), 128, iv, associatedData)
        cipher.init(true, parameters)

        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len1 = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len1)

        return iv + out
    }

    /**
     * Decrypts AES-256-GCM ciphertext (expects IV as first 12 bytes).
     */
    fun decryptAesGcm(key: ByteArray, ciphertext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(ciphertext.size > 12) { "Ciphertext too short" }

        val iv = ciphertext.copyOfRange(0, 12)
        val actualCiphertext = ciphertext.copyOfRange(12, ciphertext.size)

        val cipher = GCMBlockCipher(AESEngine())
        val parameters = AEADParameters(KeyParameter(key), 128, iv, associatedData)
        cipher.init(false, parameters)

        val out = ByteArray(cipher.getOutputSize(actualCiphertext.size))
        val len1 = cipher.processBytes(actualCiphertext, 0, actualCiphertext.size, out, 0)
        cipher.doFinal(out, len1)

        return out
    }

    // --- Hashing ---

    fun hashBlake2b(data: ByteArray, digestSize: Int = 32): ByteArray {
        val digest = Blake2bDigest(digestSize * 8)
        digest.update(data, 0, data.size)
        val out = ByteArray(digest.digestSize)
        digest.doFinal(out, 0)
        return out
    }
}

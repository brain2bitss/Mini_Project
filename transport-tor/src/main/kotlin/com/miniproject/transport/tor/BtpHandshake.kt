package com.miniproject.transport.tor

import com.miniproject.core.crypto.CryptoManager
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Handles the BTP (Bramble Transport Protocol) handshake over a socket stream.
 */
class BtpHandshake {

    companion object {
        const val VERSION: Byte = 1
    }

    /**
     * Performs a handshake.
     * @return the shared AES-256-GCM symmetric key for the session.
     */
    fun performHandshake(inputStream: InputStream, outputStream: OutputStream): ByteArray {
        // 1. Generate ephemeral X25519 keypair
        val keyPair = CryptoManager.generateX25519KeyPair()
        val privateKey = keyPair.private as X25519PrivateKeyParameters
        val publicKey = keyPair.public as X25519PublicKeyParameters

        val myPubBytes = publicKey.encoded

        // 2. Send our public key
        val outBuffer = ByteBuffer.allocate(1 + 4 + myPubBytes.size)
        outBuffer.put(VERSION)
        outBuffer.putInt(myPubBytes.size)
        outBuffer.put(myPubBytes)
        outputStream.write(outBuffer.array())
        outputStream.flush()

        // 3. Receive peer's public key
        val version = inputStream.read().toByte()
        require(version == VERSION) { "Unsupported BTP version" }

        val lenBytes = ByteArray(4)
        inputStream.read(lenBytes)
        val len = ByteBuffer.wrap(lenBytes).int

        val peerPubBytes = ByteArray(len)
        var read = 0
        while (read < len) {
            val res = inputStream.read(peerPubBytes, read, len - read)
            if (res == -1) throw Exception("Stream closed during handshake")
            read += res
        }

        val peerPublicKey = X25519PublicKeyParameters(peerPubBytes, 0)

        // 4. Compute shared secret
        val sharedSecret = CryptoManager.performX25519KeyAgreement(privateKey, peerPublicKey)

        // 5. Derive symmetric key using HKDF-SHA256
        val hkdf = org.bouncycastle.crypto.generators.HKDFBytesGenerator(org.bouncycastle.crypto.digests.SHA256Digest())
        hkdf.init(org.bouncycastle.crypto.params.HKDFParameters(sharedSecret, ByteArray(0), null))
        val derivedKey = ByteArray(32)
        hkdf.generateBytes(derivedKey, 0, 32)
        return derivedKey
    }
}

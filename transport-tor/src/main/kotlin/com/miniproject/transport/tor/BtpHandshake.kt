package com.miniproject.transport.tor

import com.miniproject.core.crypto.CryptoManager
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom

data class BtpSession(
    val sendKey: ByteArray,
    val receiveKey: ByteArray,
    val transcriptHash: ByteArray
)

class BtpHandshake {

    companion object {
        const val VERSION: Byte = 1
        private const val PUBKEY_LEN = 32
        private const val NONCE_LEN = 32
        private const val INFO_C2S = "BTP_V1_C2S"
        private const val INFO_S2C = "BTP_V1_S2C"
    }

    private val random = SecureRandom()

    fun performHandshake(
        inputStream: InputStream,
        outputStream: OutputStream,
        isAlice: Boolean
    ): BtpSession {
        val keyPair = CryptoManager.generateX25519KeyPair()
        val myPriv = keyPair.private as X25519PrivateKeyParameters
        val myPub = (keyPair.public as X25519PublicKeyParameters).encoded
        val myNonce = ByteArray(NONCE_LEN)
        random.nextBytes(myNonce)

        val myPayload = ByteArray(1 + PUBKEY_LEN + NONCE_LEN)
        myPayload[0] = VERSION
        System.arraycopy(myPub, 0, myPayload, 1, PUBKEY_LEN)
        System.arraycopy(myNonce, 0, myPayload, 1 + PUBKEY_LEN, NONCE_LEN)

        val peerPayload: ByteArray
        if (isAlice) {
            writeFrame(outputStream, myPayload)
            peerPayload = readFrame(inputStream)
        } else {
            peerPayload = readFrame(inputStream)
            writeFrame(outputStream, myPayload)
        }

        require(peerPayload.size == 1 + PUBKEY_LEN + NONCE_LEN) { "Invalid peer payload size: ${peerPayload.size}" }
        val peerVersion = peerPayload[0]
        require(peerVersion == VERSION) { "Unsupported protocol version: $peerVersion" }
        
        val peerPub = ByteArray(PUBKEY_LEN)
        System.arraycopy(peerPayload, 1, peerPub, 0, PUBKEY_LEN)
        val peerNonce = ByteArray(NONCE_LEN)
        System.arraycopy(peerPayload, 1 + PUBKEY_LEN, peerNonce, 0, NONCE_LEN)

        val initiatorPub = if (isAlice) myPub else peerPub
        val initiatorNonce = if (isAlice) myNonce else peerNonce
        val responderPub = if (isAlice) peerPub else myPub
        val responderNonce = if (isAlice) peerNonce else myNonce

        val agreement = X25519Agreement()
        agreement.init(myPriv)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPub, 0), sharedSecret, 0)

        val cmp = compareByteArrays(initiatorNonce, responderNonce)
        val salt = ByteArray(NONCE_LEN * 2)
        if (cmp <= 0) {
            System.arraycopy(initiatorNonce, 0, salt, 0, NONCE_LEN)
            System.arraycopy(responderNonce, 0, salt, NONCE_LEN, NONCE_LEN)
        } else {
            System.arraycopy(responderNonce, 0, salt, 0, NONCE_LEN)
            System.arraycopy(initiatorNonce, 0, salt, NONCE_LEN, NONCE_LEN)
        }

        val (c2sKey, s2cKey, transcript) = deriveKeys(
            sharedSecret = sharedSecret,
            salt = salt,
            initiatorPub = initiatorPub,
            initiatorNonce = initiatorNonce,
            responderPub = responderPub,
            responderNonce = responderNonce
        )

        return BtpSession(
            sendKey = if (isAlice) c2sKey else s2cKey,
            receiveKey = if (isAlice) s2cKey else c2sKey,
            transcriptHash = transcript
        )
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val aa = a[i].toInt() and 0xFF
            val bb = b[i].toInt() and 0xFF
            if (aa != bb) return aa.compareTo(bb)
        }
        return a.size.compareTo(b.size)
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r == -1) throw java.io.EOFException("Stream closed at $read/$n")
            read += r
        }
        return buf
    }

    private fun writeFrame(output: OutputStream, payload: ByteArray) {
        val len = ByteBuffer.allocate(4).putInt(payload.size).array()
        output.write(len)
        output.write(payload)
        output.flush()
    }

    private fun readFrame(input: InputStream): ByteArray {
        val lenBuf = readExactly(input, 4)
        val len = ByteBuffer.wrap(lenBuf).int
        require(len in 1..4096) { "Invalid frame length: $len" }
        return readExactly(input, len)
    }

    private fun deriveKeys(
        sharedSecret: ByteArray,
        salt: ByteArray,
        initiatorPub: ByteArray,
        initiatorNonce: ByteArray,
        responderPub: ByteArray,
        responderNonce: ByteArray
    ): Triple<ByteArray, ByteArray, ByteArray> {
        val hkdfC2S = HKDFBytesGenerator(SHA256Digest())
        hkdfC2S.init(HKDFParameters(sharedSecret, salt, INFO_C2S.toByteArray(Charsets.UTF_8)))
        val c2sKey = ByteArray(32)
        hkdfC2S.generateBytes(c2sKey, 0, 32)

        val hkdfS2C = HKDFBytesGenerator(SHA256Digest())
        hkdfS2C.init(HKDFParameters(sharedSecret, salt, INFO_S2C.toByteArray(Charsets.UTF_8)))
        val s2cKey = ByteArray(32)
        hkdfS2C.generateBytes(s2cKey, 0, 32)

        val digest = SHA256Digest()
        digest.update(initiatorPub, 0, initiatorPub.size)
        digest.update(initiatorNonce, 0, initiatorNonce.size)
        digest.update(responderPub, 0, responderPub.size)
        digest.update(responderNonce, 0, responderNonce.size)
        val transcriptHash = ByteArray(digest.digestSize)
        digest.doFinal(transcriptHash, 0)

        return Triple(c2sKey, s2cKey, transcriptHash)
    }
}

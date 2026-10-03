package com.miniproject.transport.tor.btp

import com.miniproject.core.crypto.CryptoManager
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object BtpFrameCodec {

    const val HEADER_SIZE = 38

    fun encode(frame: BtpFrame): ByteArray {
        require(frame.msgId.size == 16) { "msgId must be exactly 16 bytes" }
        require(frame.payload.size <= BtpLimits.MAX_FRAME_WIRE_SIZE) {
            "Payload size (${frame.payload.size}) exceeds max wire size (${BtpLimits.MAX_FRAME_WIRE_SIZE})"
        }

        val buffer = ByteBuffer.allocate(HEADER_SIZE + frame.payload.size)
        buffer.put(frame.version)
        buffer.put(frame.type.code)
        buffer.putInt(frame.streamId)
        buffer.putLong(frame.sequenceNumber)
        buffer.put(frame.msgId)
        buffer.putShort(frame.chunkIndex.toShort())
        buffer.putShort(frame.totalChunks.toShort())
        buffer.putInt(frame.payload.size)
        buffer.put(frame.payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): BtpFrame {
        require(bytes.size >= HEADER_SIZE) {
            "Frame too short: ${bytes.size} bytes (minimum $HEADER_SIZE)"
        }
        val buffer = ByteBuffer.wrap(bytes)
        val version = buffer.get()
        require(version == BtpLimits.PROTOCOL_VERSION) {
            "Unsupported protocol version: $version"
        }
        val typeCode = buffer.get()
        val type = BtpFrameType.fromCode(typeCode)
        val streamId = buffer.int
        val sequenceNumber = buffer.long
        val msgId = ByteArray(16)
        buffer.get(msgId)
        val chunkIndex = buffer.short.toInt() and 0xFFFF
        val totalChunks = buffer.short.toInt() and 0xFFFF
        val payloadLength = buffer.int
        require(payloadLength >= 0 && payloadLength <= BtpLimits.MAX_FRAME_WIRE_SIZE) {
            "Invalid payload length: $payloadLength"
        }
        require(buffer.remaining() == payloadLength) {
            "Payload length mismatch: header says $payloadLength, buffer has ${buffer.remaining()}"
        }
        val payload = ByteArray(payloadLength)
        buffer.get(payload)

        return BtpFrame(
            version = version,
            type = type,
            streamId = streamId,
            sequenceNumber = sequenceNumber,
            msgId = msgId,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            payload = payload
        )
    }

    fun writeEncryptedFrame(output: OutputStream, frame: BtpFrame, sendKey: ByteArray) {
        val plaintext = encode(frame)
        val ciphertext = CryptoManager.encryptAesGcm(sendKey, plaintext)
        val lenBuf = ByteBuffer.allocate(4).putInt(ciphertext.size).array()
        output.write(lenBuf)
        output.write(ciphertext)
        output.flush()
    }

    fun readEncryptedFrame(input: InputStream, receiveKey: ByteArray): BtpFrame {
        val lenBuf = readExactly(input, 4)
        val frameLen = ByteBuffer.wrap(lenBuf).int
        require(frameLen in 1..BtpLimits.MAX_FRAME_WIRE_SIZE) {
            "Invalid encrypted frame length: $frameLen"
        }
        val ciphertext = readExactly(input, frameLen)
        val plaintext = CryptoManager.decryptAesGcm(receiveKey, ciphertext)
        return decode(plaintext)
    }

    fun readExactly(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r == -1) throw EOFException("Stream closed at $read/$n bytes")
            read += r
        }
        return buf
    }
}

package com.miniproject.transport.tor

import com.miniproject.transport.tor.btp.*
import org.junit.Assert.*
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.security.SecureRandom

class BtpFramingTest {

    private val random = SecureRandom()

    @Test
    fun testAllFrameTypesEncodeDecode() {
        val msgId = ByteArray(16).also { random.nextBytes(it) }

        val testFrames = listOf(
            BtpFrame(
                type = BtpFrameType.DATA,
                streamId = 1,
                sequenceNumber = 42L,
                msgId = msgId,
                chunkIndex = 0,
                totalChunks = 3,
                payload = "Chunk 0 payload".toByteArray(Charsets.UTF_8)
            ),
            BtpChunker.createAckFrame(msgId, ackedChunkIndex = 0, seqNum = 100L, streamId = 1),
            BtpChunker.createAckFrame(msgId, ackedChunkIndex = 1, seqNum = 106L, totalChunks = 2, streamId = 1),
            BtpChunker.createPingFrame(seqNum = 101L, timestampMs = 123456789L, streamId = 1),
            BtpChunker.createPongFrame(seqNum = 102L, timestampMs = 123456789L, streamId = 1),
            BtpChunker.createRekeyFrame(seqNum = 103L, ephemeralPubkey = ByteArray(32) { (it + 1).toByte() }, streamId = 1),
            BtpChunker.createCloseFrame(seqNum = 104L, reasonCode = 1.toByte(), streamId = 1),
            BtpChunker.createErrorFrame(seqNum = 105L, errorCode = 404.toShort(), message = "Not Found", streamId = 1)
        )

        for (frame in testFrames) {
            val encoded = BtpFrameCodec.encode(frame)
            val decoded = BtpFrameCodec.decode(encoded)

            assertEquals("Frame type mismatch for ${frame.type}", frame.type, decoded.type)
            assertEquals("Version mismatch", frame.version, decoded.version)
            assertEquals("StreamId mismatch", frame.streamId, decoded.streamId)
            assertEquals("SequenceNumber mismatch", frame.sequenceNumber, decoded.sequenceNumber)
            assertArrayEquals("MsgId mismatch", frame.msgId, decoded.msgId)
            assertEquals("ChunkIndex mismatch", frame.chunkIndex, decoded.chunkIndex)
            assertEquals("TotalChunks mismatch", frame.totalChunks, decoded.totalChunks)
            assertArrayEquals("Payload mismatch for ${frame.type}", frame.payload, decoded.payload)
            assertEquals("Full object equals failed", frame, decoded)
        }
    }

    @Test
    fun testEncryptedFrameCodecPipedStreams() {
        // Run handshake between Alice and Bob
        val aliceOut = PipedOutputStream()
        val bobIn = PipedInputStream(aliceOut)
        val bobOut = PipedOutputStream()
        val aliceIn = PipedInputStream(bobOut)

        val executor = Executors.newFixedThreadPool(2)
        val aliceSessionFuture = executor.submit<BtpSession> {
            BtpHandshake().performHandshake(aliceIn, aliceOut, isAlice = true)
        }
        val bobSessionFuture = executor.submit<BtpSession> {
            BtpHandshake().performHandshake(bobIn, bobOut, isAlice = false)
        }

        val aliceSession = aliceSessionFuture.get(10, TimeUnit.SECONDS)
        val bobSession = bobSessionFuture.get(10, TimeUnit.SECONDS)

        // Now Alice sends an encrypted BtpFrame to Bob
        val testData = "BrambleChat End-to-End Encrypted Frame over BTP".toByteArray(Charsets.UTF_8)
        val msgId = ByteArray(16).also { random.nextBytes(it) }
        val sentFrame = BtpFrame(
            type = BtpFrameType.DATA,
            streamId = 1,
            sequenceNumber = 1L,
            msgId = msgId,
            chunkIndex = 0,
            totalChunks = 1,
            payload = testData
        )

        val bobReadFuture = executor.submit<BtpFrame> {
            BtpFrameCodec.readEncryptedFrame(bobIn, bobSession.receiveKey)
        }

        BtpFrameCodec.writeEncryptedFrame(aliceOut, sentFrame, aliceSession.sendKey)
        val receivedFrame = bobReadFuture.get(10, TimeUnit.SECONDS)

        assertEquals(sentFrame, receivedFrame)
        assertArrayEquals(testData, receivedFrame.payload)

        executor.shutdown()
    }

    @Test
    fun testChunkingSmallMessage() {
        val smallData = "Hello Bramble".toByteArray(Charsets.UTF_8)
        val chunks = BtpChunker.chunkMessage(smallData, maxChunkSize = 512)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].chunkIndex)
        assertEquals(1, chunks[0].totalChunks)
        assertArrayEquals(smallData, chunks[0].payload)
    }

    @Test
    fun testChunkingAndReassemblyLargeMessage() {
        // 50,000 bytes with 16,384 bytes max chunk size -> 4 chunks
        val largeData = ByteArray(50000)
        random.nextBytes(largeData)

        val chunks = BtpChunker.chunkMessage(largeData, maxChunkSize = BtpLimits.MAX_PAYLOAD_TOR)
        assertEquals(4, chunks.size)

        val reassembler = BtpReassembler()
        var assembled: ByteArray? = null

        for (i in 0 until chunks.size) {
            val res = reassembler.addChunk(chunks[i])
            if (i < chunks.size - 1) {
                assertNull("Should not be complete at chunk $i", res)
            } else {
                assembled = res
            }
        }

        assertNotNull("Reassembled payload must not be null", assembled)
        assertArrayEquals("Reassembled payload must match original", largeData, assembled)
    }

    @Test
    fun testReassemblyOutOfOrder() {
        val originalData = ByteArray(40000)
        random.nextBytes(originalData)

        val chunks = BtpChunker.chunkMessage(originalData, maxChunkSize = 8192)
        assertEquals(5, chunks.size)

        // Ingest chunks in reverse order: 4, 3, 2, 1, 0
        val reassembler = BtpReassembler()
        var assembled: ByteArray? = null

        for (i in (chunks.size - 1) downTo 0) {
            val res = reassembler.addChunk(chunks[i])
            if (i > 0) {
                assertNull("Should not be complete until chunk 0 is ingested", res)
            } else {
                assembled = res
            }
        }

        assertNotNull(assembled)
        assertArrayEquals(originalData, assembled)
    }

    @Test
    fun testReassemblyDuplicateChunks() {
        val originalData = "Robust P2P Chunking".toByteArray(Charsets.UTF_8)
        val chunks = BtpChunker.chunkMessage(originalData, maxChunkSize = 5)
        assertTrue(chunks.size > 1)

        val reassembler = BtpReassembler()
        // Ingest chunk 0 twice
        reassembler.addChunk(chunks[0])
        reassembler.addChunk(chunks[0])

        var assembled: ByteArray? = null
        for (i in 1 until chunks.size) {
            val res = reassembler.addChunk(chunks[i])
            if (i == chunks.size - 1) assembled = res
        }

        assertNotNull(assembled)
        assertArrayEquals(originalData, assembled)
    }

    @Test
    fun testFlowControllerSlidingWindow() {
        val controller = BtpFlowController(windowSize = 4)
        assertTrue(controller.canSend())
        assertEquals(0, controller.inFlightCount())

        val msgId = ByteArray(16)
        val frame1 = BtpFrame(type = BtpFrameType.DATA, sequenceNumber = 1L, msgId = msgId)
        val frame2 = BtpFrame(type = BtpFrameType.DATA, sequenceNumber = 2L, msgId = msgId)
        val frame3 = BtpFrame(type = BtpFrameType.DATA, sequenceNumber = 3L, msgId = msgId)
        val frame4 = BtpFrame(type = BtpFrameType.DATA, sequenceNumber = 4L, msgId = msgId)
        val frame5 = BtpFrame(type = BtpFrameType.DATA, sequenceNumber = 5L, msgId = msgId)

        controller.onFrameSent(frame1)
        controller.onFrameSent(frame2)
        controller.onFrameSent(frame3)
        assertTrue(controller.canSend())

        controller.onFrameSent(frame4)
        assertFalse("Window full at 4 frames", controller.canSend())
        assertEquals(4, controller.inFlightCount())

        // Receive ACK for frame 1
        val acked = controller.onAckReceived(1L)
        assertNotNull(acked)
        assertEquals(1L, acked?.sequenceNumber)
        assertTrue("Window should have space now", controller.canSend())
        assertEquals(3, controller.inFlightCount())

        // Cumulative ACK for 2 and 3
        val cumulative = controller.onCumulativeAckReceived(3L)
        assertEquals(2, cumulative.size)
        assertEquals(1, controller.inFlightCount())

        // Ack frame 4
        controller.onAckReceived(4L)
        assertTrue(controller.isAllAcked())
    }

    @Test
    fun testTamperedCiphertextFails() {
        val key = ByteArray(32).also { random.nextBytes(it) }
        val frame = BtpFrame(
            type = BtpFrameType.DATA,
            sequenceNumber = 1L,
            payload = "Tamper Test".toByteArray(Charsets.UTF_8)
        )

        val plaintext = BtpFrameCodec.encode(frame)
        val ciphertext = com.miniproject.core.crypto.CryptoManager.encryptAesGcm(key, plaintext)

        // Corrupt one byte in the ciphertext body
        ciphertext[15] = (ciphertext[15].toInt() xor 0xFF).toByte()

        try {
            com.miniproject.core.crypto.CryptoManager.decryptAesGcm(key, ciphertext)
            fail("Expected exception on tampered ciphertext")
        } catch (_: Exception) {
            // Expected: AES-GCM tag verification failure
        }
    }
}

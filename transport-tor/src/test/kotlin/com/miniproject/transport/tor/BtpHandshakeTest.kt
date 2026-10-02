package com.miniproject.transport.tor

import org.junit.Assert.*
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BtpHandshakeTest {

    @Test
    fun testHandshakeProducesMatchingKeys() {
        // Alice and Bob in two threads, connected by pipes.
        val aliceOut = PipedOutputStream()
        val bobIn = PipedInputStream(aliceOut)
        val bobOut = PipedOutputStream()
        val aliceIn = PipedInputStream(bobOut)

        val executor = Executors.newFixedThreadPool(2)

        val aliceFuture = executor.submit<BtpSession> {
            BtpHandshake().performHandshake(aliceIn, aliceOut, isAlice = true)
        }
        val bobFuture = executor.submit<BtpSession> {
            BtpHandshake().performHandshake(bobIn, bobOut, isAlice = false)
        }

        val alice = aliceFuture.get(10, TimeUnit.SECONDS)
        val bob = bobFuture.get(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Alice's send key must equal Bob's receive key
        assertArrayEquals("Alice.send must equal Bob.receive", alice.sendKey, bob.receiveKey)
        assertArrayEquals("Bob.send must equal Alice.receive", bob.sendKey, alice.receiveKey)
        assertArrayEquals("Transcript hashes must match", alice.transcriptHash, bob.transcriptHash)

        // Keys must differ in the two directions (no reflection attack)
        assertFalse("Send and receive keys must differ", alice.sendKey.contentEquals(alice.receiveKey))

        // Key lengths
        assertEquals(32, alice.sendKey.size)
        assertEquals(32, alice.receiveKey.size)
        assertEquals(32, alice.transcriptHash.size)
    }

    private fun runHandshakePair(): Pair<BtpSession, BtpSession> {
        val aliceOut = PipedOutputStream()
        val bobIn = PipedInputStream(aliceOut)
        val bobOut = PipedOutputStream()
        val aliceIn = PipedInputStream(bobOut)

        val executor = Executors.newFixedThreadPool(2)

        val aliceFuture = executor.submit<BtpSession> {
            BtpHandshake().performHandshake(aliceIn, aliceOut, isAlice = true)
        }
        val bobFuture = executor.submit<BtpSession> {
            BtpHandshake().performHandshake(bobIn, bobOut, isAlice = false)
        }

        val alice = aliceFuture.get(10, TimeUnit.SECONDS)
        val bob = bobFuture.get(10, TimeUnit.SECONDS)
        executor.shutdown()
        return Pair(alice, bob)
    }

    @Test
    fun testHandshakesAreNotDeterministic() {
        val (alice1, _) = runHandshakePair()
        val (alice2, _) = runHandshakePair()

        assertFalse("Session keys should differ between handshakes", alice1.sendKey.contentEquals(alice2.sendKey))
        assertFalse("Transcripts should differ between handshakes", alice1.transcriptHash.contentEquals(alice2.transcriptHash))
    }
}

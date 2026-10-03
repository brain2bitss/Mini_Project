package com.miniproject.transport.tor.btp

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * KDF utilities for the Double Ratchet protocol.
 *
 * Two KDF chains:
 *   - KDF_RK: Root Key chain — advances when a DH ratchet step occurs.
 *             Uses HKDF-SHA256 with the DH output as IKM and the root key as salt.
 *   - KDF_CK: Chain Key chain — advances with each message sent/received.
 *             Uses HMAC-SHA256 with constant input bytes (0x01 = message key, 0x02 = next chain key).
 *
 * Reference: Signal Double Ratchet specification.
 */
object RatchetKdf {

    private val INFO_RK = "BTP_RATCHET_RK".toByteArray(Charsets.UTF_8)

    /**
     * Root Key KDF — used during DH ratchet steps.
     *
     * Derives a new root key and a new chain key from the current root key
     * and a fresh DH shared secret.
     *
     * @param rootKey current root key (32 bytes)
     * @param dhOutput X25519 shared secret (32 bytes)
     * @return Pair(newRootKey, newChainKey) — both 32 bytes
     */
    fun kdfRootKey(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        require(rootKey.size == 32) { "rootKey must be 32 bytes, got ${rootKey.size}" }
        require(dhOutput.size == 32) { "dhOutput must be 32 bytes, got ${dhOutput.size}" }

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(dhOutput, rootKey, INFO_RK))
        val output = ByteArray(64)
        hkdf.generateBytes(output, 0, 64)
        return Pair(
            output.copyOfRange(0, 32),   // new root key
            output.copyOfRange(32, 64)   // new chain key
        )
    }

    /**
     * Chain Key KDF — used for each message sent/received.
     *
     * Derives a new chain key and a message key from the current chain key.
     * The message key is used for AES-256-GCM encryption of that specific message.
     * The chain key advances to the new value.
     *
     * HMAC(chainKey, 0x01) → messageKey (used once, then zeroized)
     * HMAC(chainKey, 0x02) → nextChainKey (stored, old chainKey zeroized)
     *
     * @param chainKey current chain key (32 bytes)
     * @return Pair(newChainKey, messageKey) — both 32 bytes
     */
    fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        require(chainKey.size == 32) { "chainKey must be 32 bytes, got ${chainKey.size}" }

        val messageKey = hmacSha256(chainKey, byteArrayOf(0x01))
        val newChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        return Pair(newChainKey, messageKey)
    }

    /**
     * HMAC-SHA256 keyed hash.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(key))
        hmac.update(data, 0, data.size)
        val out = ByteArray(hmac.macSize)
        hmac.doFinal(out, 0)
        return out
    }
}

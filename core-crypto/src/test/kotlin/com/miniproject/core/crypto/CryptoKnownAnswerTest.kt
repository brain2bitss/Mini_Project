package com.miniproject.core.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test

/**
 * Known-Answer Tests (KATs) using official RFC / NIST test vectors.
 * These prove the crypto primitives produce exact bit-identical output
 * to the reference specifications — not just "round-trip works."
 *
 * Sources:
 *  - X25519:      RFC 7748, Section 6.1
 *  - Ed25519:     RFC 8032, Section 7.1, Test Vector 1
 *  - AES-256-GCM: NIST SP 800-38D, Test Case 16 (256-bit key, 96-bit IV)
 *  - BLAKE2b:     RFC 7693, Appendix A
 *  - HKDF-SHA256: RFC 5869, Test Case 1
 */
class CryptoKnownAnswerTest {

    // ── helpers ──────────────────────────────────────────────────────

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
        return ByteArray(clean.length / 2) { i ->
            Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    // ── RFC 7748 §6.1 — X25519 scalar multiplication ────────────────

    @Test
    fun testX25519_RFC7748_Section6_1() {
        // Alice's private key (scalar, clamped already by BouncyCastle)
        val alicePrivateBytes = hex(
            "77076d0a7318a57d3c16c17251b26645" +
            "df4c2f87ebc0992ab177fba51db92c2a"
        )
        // Alice's expected public key  u(alice_private * 9)
        val alicePublicExpected = hex(
            "8520f0098930a754748b7ddcb43ef75a" +
            "0dbf3a0d26381af4eba4a98eaa9b4e6a"
        )
        // Bob's private key
        val bobPrivateBytes = hex(
            "5dab087e624a8a4b79e17f8b83800ee6" +
            "6f3bb1292618b6fd1c2f8b27ff88e0eb"
        )
        // Bob's expected public key
        val bobPublicExpected = hex(
            "de9edb7d7b7dc1b4d35b61c2ece43537" +
            "3f8343c85b78674dadfc7e146f882b4f"
        )
        // Shared secret
        val sharedSecretExpected = hex(
            "4a5d9d5ba4ce2de1728e3bf480350f25" +
            "e07e21c947d19e3376f09b3c1e161742"
        )

        val alicePrivate = X25519PrivateKeyParameters(alicePrivateBytes, 0)
        val alicePublic  = alicePrivate.generatePublicKey()
        val bobPrivate   = X25519PrivateKeyParameters(bobPrivateBytes, 0)
        val bobPublic    = bobPrivate.generatePublicKey()

        // Verify derived public keys match RFC vector
        assertArrayEquals(
            "Alice public key must match RFC 7748 §6.1",
            alicePublicExpected, alicePublic.encoded
        )
        assertArrayEquals(
            "Bob public key must match RFC 7748 §6.1",
            bobPublicExpected, bobPublic.encoded
        )

        // Verify shared secret (Alice side)
        val agreementA = X25519Agreement()
        agreementA.init(alicePrivate)
        val secretA = ByteArray(agreementA.agreementSize)
        agreementA.calculateAgreement(bobPublic, secretA, 0)

        // Verify shared secret (Bob side)
        val agreementB = X25519Agreement()
        agreementB.init(bobPrivate)
        val secretB = ByteArray(agreementB.agreementSize)
        agreementB.calculateAgreement(alicePublic, secretB, 0)

        assertArrayEquals(
            "Shared secret must match RFC 7748 §6.1",
            sharedSecretExpected, secretA
        )
        assertArrayEquals(
            "Both sides must derive same secret",
            secretA, secretB
        )
    }

    // ── RFC 8032 §7.1 Test 1 — Ed25519 sign/verify ──────────────────

    @Test
    fun testEd25519_RFC8032_TestVector1() {
        val secretKeySeed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val publicKeyExpected = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val signatureExpected = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        val message = ByteArray(0)

        val privateKey = Ed25519PrivateKeyParameters(secretKeySeed, 0)
        val publicKey  = privateKey.generatePublicKey()

        assertArrayEquals(
            "Ed25519 public key must match RFC 8032 §7.1 test 1",
            publicKeyExpected, publicKey.encoded
        )

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()

        assertArrayEquals(
            "Ed25519 signature must match RFC 8032 §7.1 test 1",
            signatureExpected, signature
        )

        val verifier = Ed25519Signer()
        verifier.init(false, publicKey)
        verifier.update(message, 0, message.size)
        assertTrue(
            "Ed25519 verification must succeed for RFC 8032 test vector",
            verifier.verifySignature(signature)
        )
    }



    // ── NIST SP 800-38D Test Case 16 — AES-256-GCM ──────────────────

    @Test
    fun testAes256Gcm_NIST_TestCase16() {
        // Test Case 16 from
        // https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Algorithm-Validation-Program/documents/mac/gcmtestvectors.zip
        val key   = hex("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        val iv    = hex("cafebabefacedbaddecaf888")
        val pt    = hex(
            "d9313225f88406e5a55909c5aff5269a" +
            "86a7a9531534f7da2e4c303d8a318a72" +
            "1c3c0c95956809532fcf0e2449a6b525" +
            "b16aedf5aa0de657ba637b39"
        )
        val aad   = hex(
            "feedfacedeadbeeffeedfacedeadbeef" +
            "abaddad2"
        )
        val ctExpected = hex(
            "522dc1f099567d07f47f37a32a84427d" +
            "643a8cdcbfe5c0c97598a2bd2555d1aa" +
            "8cb08e48590dbb3da7b08b1056828838" +
            "c5f61e6393ba7a0abcc9f662"
        )
        val tagExpected = hex("76fc6ece0f4e1768cddf8853bb2d551b")

        // Encrypt
        val cipher = GCMBlockCipher(AESEngine())
        val params = AEADParameters(KeyParameter(key), 128, iv, aad)
        cipher.init(true, params)
        val out = ByteArray(cipher.getOutputSize(pt.size))
        var len = cipher.processBytes(pt, 0, pt.size, out, 0)
        len += cipher.doFinal(out, len)

        // out = ciphertext || tag
        val ct  = out.copyOfRange(0, pt.size)
        val tag = out.copyOfRange(pt.size, out.size)

        assertArrayEquals(
            "AES-256-GCM ciphertext must match NIST Test Case 16",
            ctExpected, ct
        )
        assertArrayEquals(
            "AES-256-GCM auth tag must match NIST Test Case 16",
            tagExpected, tag
        )

        // Decrypt and verify
        val decCipher = GCMBlockCipher(AESEngine())
        decCipher.init(false, params)
        val decOut = ByteArray(decCipher.getOutputSize(out.size))
        var dLen = decCipher.processBytes(out, 0, out.size, decOut, 0)
        dLen += decCipher.doFinal(decOut, dLen)

        assertArrayEquals(
            "AES-256-GCM decryption must recover original plaintext",
            pt, decOut.copyOfRange(0, dLen)
        )
    }

    // ── RFC 7693 Appendix A — BLAKE2b self-test (empty input) ────────

    @Test
    fun testBlake2b_RFC7693_AppendixA() {
        // BLAKE2b-512 of empty string ""
        // From reference: https://www.blake2.net/ and RFC 7693 appendix
        val expectedHash512 = hex(
            "786a02f742015903c6c6fd852552d272" +
            "912f4740e15847618a86e217f71f5419" +
            "d25e1031afee585313896444934eb04b" +
            "903a685b1448b755d56f701afe9be2ce"
        )

        val hash = CryptoManager.hashBlake2b(ByteArray(0), digestSize = 64)
        assertArrayEquals(
            "BLAKE2b-512('') must match RFC 7693 known answer",
            expectedHash512, hash
        )
    }

    @Test
    fun testBlake2b_abc() {
        val expectedHash = hex("ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923")
        val hash = CryptoManager.hashBlake2b("abc".toByteArray(Charsets.US_ASCII), digestSize = 64)

        assertArrayEquals(
            "BLAKE2b-512('abc') must match canonical reference vector",
            expectedHash, hash
        )
    }

    // ── RFC 5869 Test Case 1 — HKDF-SHA256 ──────────────────────────

    @Test
    fun testHkdfSha256_RFC5869_TestCase1() {
        val ikm  = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expectedOkm = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        )

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        val okm = ByteArray(42) // L = 42 per RFC 5869 TC1
        hkdf.generateBytes(okm, 0, okm.size)

        assertArrayEquals(
            "HKDF-SHA256 OKM must match RFC 5869 Test Case 1",
            expectedOkm, okm
        )
    }
}

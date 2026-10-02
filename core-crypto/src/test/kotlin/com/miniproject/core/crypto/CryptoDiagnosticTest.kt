package com.miniproject.core.crypto

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.junit.Test

/**
 * Diagnostic: dump actual hex from BouncyCastle so we can compare
 * against RFC values and figure out the discrepancy.
 */
class CryptoDiagnosticTest {

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
        return ByteArray(clean.length / 2) { i ->
            Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16).toByte()
        }
    }

    @Test
    fun dumpEd25519() {
        val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        val pub = priv.generatePublicKey()
        println("=== Ed25519 Diagnostic ===")
        println("Seed:       ${seed.toHex()}")
        println("Public key: ${pub.encoded.toHex()}")
        println("RFC expect: d75a980182b10ab7d54bfed3c964073a0ee172f3daa3f4a18446b0b8d183f8e8")
        println("Match:      ${pub.encoded.toHex() == "d75a980182b10ab7d54bfed3c964073a0ee172f3daa3f4a18446b0b8d183f8e8"}")
        println("BC version: ${org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME}")
        println()
    }

    @Test
    fun dumpBlake2b() {
        // Test empty string first (this passes, so BC BLAKE2b works for empty)
        val emptyDigest = Blake2bDigest(512)
        val emptyOut = ByteArray(64)
        emptyDigest.doFinal(emptyOut, 0)
        println("=== BLAKE2b Diagnostic ===")
        println("BLAKE2b-512(''): ${emptyOut.toHex()}")
        println("RFC expect:      786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce")
        println("Match empty:     ${emptyOut.toHex() == "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce"}")
        println()

        // Now test "abc"
        val abcDigest = Blake2bDigest(512)
        val abc = "abc".toByteArray(Charsets.US_ASCII)
        abcDigest.update(abc, 0, abc.size)
        val abcOut = ByteArray(64)
        abcDigest.doFinal(abcOut, 0)
        println("BLAKE2b-512('abc'): ${abcOut.toHex()}")
        println("Reference expect:   ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6886a7e7971af36a21796ed2bce5252f260ae6e01463f65d33be688925a4543b6f9be2ce42")
        println("Match abc:          ${abcOut.toHex() == "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6886a7e7971af36a21796ed2bce5252f260ae6e01463f65d33be688925a4543b6f9be2ce42"}")
        println()

        // Also test via CryptoManager wrapper to see if wrapper introduces a bug
        val wrapperHash = CryptoManager.hashBlake2b(abc, digestSize = 64)
        println("Via CryptoManager:  ${wrapperHash.toHex()}")
        println("Direct == Wrapper:  ${abcOut.toHex() == wrapperHash.toHex()}")
    }
}

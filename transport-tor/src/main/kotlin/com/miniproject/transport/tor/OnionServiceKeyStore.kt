package com.miniproject.transport.tor

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Persists the v3 Onion Service ED25519 private key so the .onion
 * address remains stable across app restarts.
 */
class OnionServiceKeyStore(private val context: Context) {

    companion object {
        private const val TAG = "OnionServiceKeyStore"
        private const val FILE_NAME = "onion_service_v3_key.txt"
    }

    private val keyFile = File(context.filesDir, FILE_NAME)

    fun loadPrivateKey(): String? {
        if (!keyFile.exists()) return null
        return try {
            keyFile.readText().trim().ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read key file", e)
            null
        }
    }

    fun savePrivateKey(privateKey: String) {
        try {
            keyFile.writeText(privateKey)
            keyFile.setReadable(true, true)
            keyFile.setWritable(true, true)
            Log.i(TAG, "Saved onion service private key (${privateKey.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save key", e)
        }
    }
}

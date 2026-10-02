package com.miniproject.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.miniproject.transport.tor.TorManager
import kotlin.concurrent.thread

class DebugTorActivity : ComponentActivity() {

    private lateinit var output: TextView
    private lateinit var torManager: TorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val startButton = Button(this).apply {
            text = "Start Tor + BTP Listener"
            setOnClickListener { startTor() }
        }
        layout.addView(startButton)

        val btpTestButton = Button(this).apply {
            text = "BTP Self-Test"
            setOnClickListener { runBtpSelfTest() }
        }
        layout.addView(btpTestButton)

        val stopButton = Button(this).apply {
            text = "Stop Tor"
            setOnClickListener {
                if (::torManager.isInitialized) {
                    torManager.stop()
                    appendLog("Stopped Tor")
                }
            }
        }
        layout.addView(stopButton)

        output = TextView(this).apply {
            text = "Ready.\n"
            textSize = 12f
        }

        val scroll = ScrollView(this).apply {
            addView(output)
        }
        layout.addView(scroll)

        setContentView(layout)
    }

    private fun appendLog(line: String) {
        Log.i("DebugTorActivity", line)
        runOnUiThread {
            output.append("$line\n")
        }
    }

    private fun startTor() {
        appendLog("Starting Tor...")
        torManager = TorManager(applicationContext)

        // Start the BTP protocol listener BEFORE Tor, so it's ready
        // when the onion service descriptor propagates.
        torManager.startBtpListener { event -> appendLog("[BTP] $event") }

        thread {
            try {
                torManager.start()
                appendLog("start() returned")
                appendLog("onionAddress = ${torManager.onionAddress}")

                // Wait for descriptor publication
                appendLog("Waiting for descriptor publication (up to 2 min)...")
                val published = torManager.waitForDescriptorUpload(
                    timeoutSeconds = 120,
                    onProgress = { msg -> appendLog(msg) }
                )
                if (published) {
                    appendLog("✓ Onion service is LIVE and reachable!")
                    appendLog("Ready for BTP Self-Test — tap the button above.")
                } else {
                    appendLog("⚠ Descriptor not confirmed — may need more time")
                }
            } catch (e: Exception) {
                Log.e("DebugTorActivity", "Tor start failed", e)
                appendLog("FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * Connects to our OWN onion address through Tor SOCKS, performs a
     * BTP handshake as initiator, sends "Hello BTP!" encrypted with
     * AES-256-GCM, and reads back the echo — proving the entire stack:
     *
     *   App → SOCKS → Tor circuit → Onion Service → BTP Handshake
     *   → AES-GCM encrypt → transmit → AES-GCM decrypt → echo
     */
    private fun runBtpSelfTest() {
        if (!::torManager.isInitialized || torManager.onionAddress == null) {
            appendLog("ERROR: Start Tor first!")
            return
        }
        val address = torManager.onionAddress!!
        appendLog("═══ BTP Self-Test ═══")
        appendLog("Connecting to $address via Tor SOCKS...")

        thread {
            val echo = torManager.btpConnectAndSend(
                peerOnionAddress = address,
                message = "Hello BTP!",
                onEvent = { msg -> appendLog("[BTP-Client] $msg") }
            )
            if (echo != null && echo == "echo:Hello BTP!") {
                appendLog("✓ BTP Self-Test PASSED — full end-to-end encrypted echo over Tor!")
            } else if (echo != null) {
                appendLog("⚠ BTP Self-Test: unexpected echo: \"$echo\"")
            } else {
                appendLog("✗ BTP Self-Test FAILED — see log above")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT stop TorManager here. Tor runs as a service and should
        // continue running even when the activity is closed.
    }
}

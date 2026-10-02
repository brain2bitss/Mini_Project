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
            text = "Start Tor"
            setOnClickListener { startTor() }
        }
        layout.addView(startButton)

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

        // Fix 2: Start the TCP listener FIRST so incoming connections have
        // somewhere to land once the onion service descriptor propagates.
        torManager.listenForPeers { socket ->
            try {
                val reader = socket.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return@listenForPeers
                // Consume remaining HTTP headers
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }
                appendLog("Incoming: $requestLine")

                val body = "OK"
                val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: ${body.length}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    body
                socket.getOutputStream().write(response.toByteArray())
                socket.getOutputStream().flush()
                socket.close()
            } catch (e: Exception) {
                Log.e("DebugTorActivity", "Connection handler error", e)
            }
        }
        appendLog("Listener started on port 7654")

        thread {
            try {
                torManager.start()
                appendLog("start() returned")
                appendLog("onionAddress = ${torManager.onionAddress}")

                // Fix 4: Wait for the descriptor to propagate to HSDirs
                appendLog("Waiting for descriptor publication (up to 2 min)...")
                val published = torManager.waitForDescriptorUpload(
                    timeoutSeconds = 120,
                    onProgress = { msg -> appendLog(msg) }
                )
                if (published) {
                    appendLog("✓ Onion service is LIVE and reachable!")
                } else {
                    appendLog("⚠ Descriptor not confirmed — may need more time")
                }
            } catch (e: Exception) {
                Log.e("DebugTorActivity", "Tor start failed", e)
                appendLog("FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do NOT stop TorManager here. Tor runs as a service and should
        // continue running even when the activity is closed.
        // TorManager will be stopped explicitly by the user or when the app
        // process dies.
    }
}

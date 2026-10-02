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
        thread {
            try {
                torManager.start()
                appendLog("start() returned")
                appendLog("onionAddress = ${torManager.onionAddress}")
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

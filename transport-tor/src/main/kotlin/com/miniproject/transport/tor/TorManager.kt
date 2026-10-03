/*
javap org.torproject.jni.TorService:
Compiled from "TorService.java"
public class org.torproject.jni.TorService extends android.app.Service implements net.freehaven.tor.control.TorControlCommands {
  public static final java.lang.String TAG;
  public static final java.lang.String VERSION_NAME;
  public static final java.lang.String ACTION_START;
  public static final java.lang.String ACTION_STOP;
  public static final java.lang.String ACTION_STATUS;
  public static final java.lang.String ACTION_ERROR;
  public static final java.lang.String EXTRA_STATUS;
  public static final java.lang.String EXTRA_SERVICE_PACKAGE_NAME;
  public static final java.lang.String STATUS_OFF;
  public static final java.lang.String STATUS_ON;
  public static final java.lang.String STATUS_STARTING;
  public static final java.lang.String STATUS_STOPPING;
  static volatile java.lang.String currentStatus;
  public static final java.lang.String STATUS_CLIENT_CIRCUIT_ESTABLISHED;
  public org.torproject.jni.TorService();
  public static java.io.File getTorrc(android.content.Context);
  public static java.io.File getDefaultsTorrc(android.content.Context);
  public static java.lang.String getBroadcastPackageName(android.content.Context);
  public android.os.IBinder onBind(android.content.Intent);
  public void onCreate();
  public int onStartCommand(android.content.Intent, int, int);
  public void onDestroy();
  public int getSocksPort();
  public int getHttpTunnelPort();
  public java.lang.String getInfo(java.lang.String);
  public net.freehaven.tor.control.TorControlConnection getTorControlConnection();
  static {};
}
*/
package com.miniproject.transport.tor

import android.content.Context
import android.util.Log
import com.miniproject.core.crypto.CryptoManager
import com.miniproject.transport.tor.btp.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Interface for Tor transport layer.
 */
interface TorTransport {
    val onionAddress: String?
    fun start()
    fun connectToPeer(onionHostname: String, port: Int = 80): Socket
    fun listenForPeers(onConnection: (Socket) -> Unit)
}

/**
 * Real Tor transport implementation backed by org.torproject.jni.TorService.
 */
class TorManager(private val context: Context) : TorTransport {

    companion object {
        private const val TAG = "TorManager"
        private const val LOCAL_HS_PORT = 7654
    }

    private val serviceManager = TorServiceManager(context)
    private val keyStore = OnionServiceKeyStore(context)
    private var boundTorService: org.torproject.jni.TorService? = null
    private var socksPort: Int = 0
    private var controlPort: Int = 0

    override var onionAddress: String? = null
        private set

    override fun start() {
        runBlocking {
            val service = serviceManager.startAndBind(timeoutMillis = 180_000)
            boundTorService = service
            socksPort = service.socksPort
            Log.i(TAG, "Tor SOCKS port: $socksPort")
            require(socksPort > 0) { "Tor did not provide a valid SOCKS port (got $socksPort)" }

            val control = service.torControlConnection
                ?: throw IllegalStateException("TorControlConnection not available")

            val savedKey = keyStore.loadPrivateKey()
            val keySpec = if (savedKey != null) {
                Log.i(TAG, "Restoring Onion Service from saved key (${savedKey.length} chars)")
                "ED25519-V3:$savedKey"
            } else {
                Log.i(TAG, "Creating NEW Onion Service")
                "NEW:ED25519-V3"
            }

            val ports = mapOf(80 to "127.0.0.1:$LOCAL_HS_PORT")
            val result = control.addOnion(keySpec, ports)
            Log.i(TAG, "ADD_ONION response: $result")

            val serviceId = (result["onionAddress"] ?: result["ServiceID"])
                ?.substringAfter("onionAddress=")?.substringBefore(":")?.trim()
                ?: throw IllegalStateException("No service ID in ADD_ONION response: $result")
            onionAddress = "$serviceId.onion"

            val privKey = result["onionPrivKey"] ?: result["PrivateKey"]
            if (savedKey == null && privKey != null) {
                val base64Key = privKey.substringAfter("ED25519-V3:").trim()
                if (base64Key.isNotEmpty() && base64Key != privKey) {
                    keyStore.savePrivateKey(base64Key)
                    Log.i(TAG, "Saved base64 key of length ${base64Key.length}")
                } else {
                    Log.e(TAG, "Failed to extract base64 from: ${privKey.take(30)}...")
                }
            }

            Log.i(TAG, "Onion address: $onionAddress")
        }
    }

    override fun connectToPeer(onionHostname: String, port: Int): Socket {
        require(socksPort > 0) { "TorManager not started; call start() first" }
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val socket = Socket(proxy)
        socket.soTimeout = 60_000
        socket.connect(InetSocketAddress.createUnresolved(onionHostname, port), 60_000)
        return socket
    }

    override fun listenForPeers(onConnection: (Socket) -> Unit) {
        thread(name = "TorManager-Listener") {
            try {
                val serverSocket = ServerSocket(LOCAL_HS_PORT)
                Log.i(TAG, "Listening for peers on local port $LOCAL_HS_PORT")
                while (true) {
                    val client = serverSocket.accept()
                    thread(name = "TorManager-Handler") {
                        try {
                            onConnection(client)
                        } catch (e: Exception) {
                            Log.e(TAG, "Connection handler error", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "listenForPeers failed", e)
            }
        }
    }

    /**
     * Starts a BTP protocol listener on the hidden service port.
     * For each inbound connection:
     *   1. Performs a BtpHandshake (X25519 + HKDF) — phone is responder (isAlice=false)
     *   2. Reads one length-prefixed encrypted frame, decrypts with receiveKey
     *   3. Logs the plaintext
     *   4. Re-encrypts with sendKey and sends back (echo)
     *   5. Closes the socket
     *
     * This proves the full BTP handshake works end-to-end over Tor.
     */
    fun startBtpListener(onEvent: ((String) -> Unit)? = null) {
        thread(name = "TorManager-BtpListener") {
            try {
                val serverSocket = ServerSocket(LOCAL_HS_PORT)
                Log.i(TAG, "BTP listener started on port $LOCAL_HS_PORT")
                onEvent?.invoke("BTP listener started on port $LOCAL_HS_PORT")
                val reassembler = BtpReassembler()
                while (true) {
                    val client = serverSocket.accept()
                    Log.i(TAG, "BTP: incoming connection from ${client.remoteSocketAddress}")
                    onEvent?.invoke("BTP: incoming connection")
                    thread(name = "TorManager-BtpHandler") {
                        try {
                            client.soTimeout = 60_000
                            // Step 1: BTP handshake — we are responder (isAlice = false)
                            val handshake = BtpHandshake()
                            val session = handshake.performHandshake(
                                client.getInputStream(),
                                client.getOutputStream(),
                                isAlice = false
                            )
                            val txHashHex = session.transcriptHash.take(8)
                                .joinToString("") { "%02x".format(it) }
                            Log.i(TAG, "BTP handshake complete — transcript: $txHashHex")
                            onEvent?.invoke("Handshake OK — transcript: $txHashHex")

                            val inStream = client.getInputStream()
                            val outStream = client.getOutputStream()

                            // Step 2: Read first encrypted frame (4-byte length + ciphertext)
                            val lenBuf = ByteArray(4)
                            var read = 0
                            while (read < 4) {
                                val r = inStream.read(lenBuf, read, 4 - read)
                                if (r == -1) throw IOException("EOF reading frame length")
                                read += r
                            }
                            val frameLen = java.nio.ByteBuffer.wrap(lenBuf).int
                            require(frameLen in 1..BtpLimits.MAX_FRAME_WIRE_SIZE) { "Invalid frame length: $frameLen" }
                            val ciphertext = ByteArray(frameLen)
                            read = 0
                            while (read < frameLen) {
                                val r = inStream.read(ciphertext, read, frameLen - read)
                                if (r == -1) throw IOException("EOF reading frame body")
                                read += r
                            }

                            // Step 3: Decrypt with receiveKey
                            val plaintext = CryptoManager.decryptAesGcm(session.receiveKey, ciphertext)

                            if (plaintext.size >= BtpFrameCodec.HEADER_SIZE && plaintext[0] == BtpLimits.PROTOCOL_VERSION) {
                                // Structured BTP Frame
                                val firstFrame = BtpFrameCodec.decode(plaintext)
                                Log.i(TAG, "BTP received frame: type=${firstFrame.type}, chunk=${firstFrame.chunkIndex + 1}/${firstFrame.totalChunks}")
                                onEvent?.invoke("Received: ${firstFrame.type} chunk ${firstFrame.chunkIndex + 1}/${firstFrame.totalChunks}")

                                if (firstFrame.type == BtpFrameType.DATA) {
                                    // Send ACK for first chunk
                                    val ackFrame = BtpChunker.createAckFrame(
                                        msgId = firstFrame.msgId,
                                        ackedChunkIndex = firstFrame.chunkIndex,
                                        seqNum = 1L,
                                        totalChunks = firstFrame.totalChunks
                                    )
                                    BtpFrameCodec.writeEncryptedFrame(outStream, ackFrame, session.sendKey)
                                    Log.i(TAG, "BTP sent ACK for chunk ${firstFrame.chunkIndex + 1}/${firstFrame.totalChunks}")

                                    var completePayload = reassembler.addChunk(firstFrame)
                                    var ackSeq = 2L

                                    while (completePayload == null) {
                                        val nextFrame = BtpFrameCodec.readEncryptedFrame(inStream, session.receiveKey)
                                        if (nextFrame.type == BtpFrameType.DATA) {
                                            val ack = BtpChunker.createAckFrame(
                                                msgId = nextFrame.msgId,
                                                ackedChunkIndex = nextFrame.chunkIndex,
                                                seqNum = ackSeq++,
                                                totalChunks = nextFrame.totalChunks
                                            )
                                            BtpFrameCodec.writeEncryptedFrame(outStream, ack, session.sendKey)
                                            Log.i(TAG, "BTP sent ACK for chunk ${nextFrame.chunkIndex + 1}/${nextFrame.totalChunks}")
                                            completePayload = reassembler.addChunk(nextFrame)
                                        } else {
                                            break
                                        }
                                    }

                                    if (completePayload != null) {
                                        val message = String(completePayload, Charsets.UTF_8)
                                        val preview = if (message.length > 40) message.take(40) + "..." else message
                                        Log.i(TAG, "BTP reassembled complete message: \"$preview\" (${completePayload.size} bytes)")
                                        onEvent?.invoke("Reassembled: \"$preview\" (${completePayload.size}B)")

                                        // Echo back
                                        val echoPayload = "echo:$message".toByteArray(Charsets.UTF_8)
                                        val echoFrames = BtpChunker.chunkMessage(echoPayload, maxChunkSize = BtpLimits.MAX_PAYLOAD_TOR)
                                        for (echoFrame in echoFrames) {
                                            BtpFrameCodec.writeEncryptedFrame(outStream, echoFrame, session.sendKey)
                                        }
                                        Log.i(TAG, "BTP sent echo in ${echoFrames.size} frame(s)")
                                        onEvent?.invoke("Sent echo (${echoFrames.size} frames)")
                                    }
                                }
                            } else {
                                // Legacy raw message echo
                                val message = String(plaintext, Charsets.UTF_8)
                                Log.i(TAG, "BTP received raw: \"$message\" (${plaintext.size} bytes)")
                                onEvent?.invoke("Received: \"$message\"")

                                val echoCiphertext = CryptoManager.encryptAesGcm(
                                    session.sendKey,
                                    "echo:$message".toByteArray(Charsets.UTF_8)
                                )
                                val echoLen = java.nio.ByteBuffer.allocate(4).putInt(echoCiphertext.size).array()
                                outStream.write(echoLen)
                                outStream.write(echoCiphertext)
                                outStream.flush()
                                Log.i(TAG, "BTP sent echo (${echoCiphertext.size} bytes)")
                                onEvent?.invoke("Sent echo (${echoCiphertext.size} bytes)")
                            }

                            client.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "BTP handler error", e)
                            onEvent?.invoke("BTP error: ${e.message}")
                            try { client.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "BTP listener failed", e)
                onEvent?.invoke("BTP listener failed: ${e.message}")
            }
        }
    }

    /**
     * Connects to a peer's onion address, performs a BTP handshake as initiator,
     * sends one encrypted message, and reads back the echo.
     * Used for testing the full BTP stack over Tor.
     *
     * @return the decrypted echo response, or null on failure
     */
    fun btpConnectAndSend(
        peerOnionAddress: String,
        message: String,
        onEvent: ((String) -> Unit)? = null
    ): String? {
        require(socksPort > 0) { "TorManager not started" }
        try {
            onEvent?.invoke("Connecting to $peerOnionAddress...")
            val socket = connectToPeer(peerOnionAddress, 80)
            socket.soTimeout = 60_000

            // Handshake — we are initiator (isAlice = true)
            val handshake = BtpHandshake()
            val session = handshake.performHandshake(
                socket.getInputStream(),
                socket.getOutputStream(),
                isAlice = true
            )
            val txHashHex = session.transcriptHash.take(8)
                .joinToString("") { "%02x".format(it) }
            Log.i(TAG, "BTP handshake complete (initiator) — transcript: $txHashHex")
            onEvent?.invoke("Handshake OK — transcript: $txHashHex")

            // Encrypt and send
            val ciphertext = CryptoManager.encryptAesGcm(
                session.sendKey,
                message.toByteArray(Charsets.UTF_8)
            )
            val lenBuf = java.nio.ByteBuffer.allocate(4).putInt(ciphertext.size).array()
            socket.getOutputStream().write(lenBuf)
            socket.getOutputStream().write(ciphertext)
            socket.getOutputStream().flush()
            Log.i(TAG, "BTP sent: \"$message\" (${ciphertext.size} bytes encrypted)")
            onEvent?.invoke("Sent: \"$message\"")

            // Read echo
            val echoLenBuf = ByteArray(4)
            var read = 0
            while (read < 4) {
                val r = socket.getInputStream().read(echoLenBuf, read, 4 - read)
                if (r == -1) throw IOException("EOF reading echo length")
                read += r
            }
            val echoLen = java.nio.ByteBuffer.wrap(echoLenBuf).int
            require(echoLen in 1..65536) { "Invalid echo length: $echoLen" }
            val echoCiphertext = ByteArray(echoLen)
            read = 0
            while (read < echoLen) {
                val r = socket.getInputStream().read(echoCiphertext, read, echoLen - read)
                if (r == -1) throw IOException("EOF reading echo body")
                read += r
            }

            val echoPlaintext = CryptoManager.decryptAesGcm(session.receiveKey, echoCiphertext)
            val echoMessage = String(echoPlaintext, Charsets.UTF_8)
            Log.i(TAG, "BTP received echo: \"$echoMessage\"")
            onEvent?.invoke("Echo: \"$echoMessage\"")

            socket.close()
            return echoMessage
        } catch (e: Exception) {
            Log.e(TAG, "BTP connect failed", e)
            onEvent?.invoke("BTP connect failed: ${e.message}")
            return null
        }
    }

    /**
     * Connects to a peer's onion address, performs a BTP handshake as initiator,
     * splits [data] into chunked BTP frames, transmits each frame encrypted,
     * verifies ACKs from the peer, and reads back the reassembled echo.
     */
    fun btpSendChunkedMessage(
        peerOnionAddress: String,
        data: ByteArray,
        maxChunkSize: Int = BtpLimits.MAX_PAYLOAD_TOR,
        onEvent: ((String) -> Unit)? = null
    ): ByteArray? {
        require(socksPort > 0) { "TorManager not started" }
        try {
            onEvent?.invoke("Connecting to $peerOnionAddress...")
            val socket = connectToPeer(peerOnionAddress, 80)
            socket.soTimeout = 60_000

            // Handshake — initiator (isAlice = true)
            val handshake = BtpHandshake()
            val session = handshake.performHandshake(
                socket.getInputStream(),
                socket.getOutputStream(),
                isAlice = true
            )
            val txHashHex = session.transcriptHash.take(8).joinToString("") { "%02x".format(it) }
            Log.i(TAG, "BTP handshake complete (initiator) — transcript: $txHashHex")
            onEvent?.invoke("Handshake OK — transcript: $txHashHex")

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            // Chunk data into BTP frames
            val frames = BtpChunker.chunkMessage(
                payload = data,
                maxChunkSize = maxChunkSize
            )
            val totalChunks = frames.size
            Log.i(TAG, "BTP sending ${data.size} bytes in $totalChunks chunk(s)")
            onEvent?.invoke("Sending ${data.size}B in $totalChunks chunk(s)...")

            val flowController = BtpFlowController()

            for (frame in frames) {
                BtpFrameCodec.writeEncryptedFrame(outStream, frame, session.sendKey)
                flowController.onFrameSent(frame)
                Log.i(TAG, "BTP sent chunk ${frame.chunkIndex + 1}/$totalChunks (${frame.payload.size}B)")
                onEvent?.invoke("Sent chunk ${frame.chunkIndex + 1}/$totalChunks (${frame.payload.size}B)")

                // Read ACK frame from responder
                val ackFrame = BtpFrameCodec.readEncryptedFrame(inStream, session.receiveKey)
                require(ackFrame.type == BtpFrameType.ACK) { "Expected ACK frame, got ${ackFrame.type}" }
                flowController.onAckReceived(frame.sequenceNumber)
                Log.i(TAG, "BTP received ACK for chunk ${frame.chunkIndex + 1}/$totalChunks")
                onEvent?.invoke("ACK for chunk ${frame.chunkIndex + 1}/$totalChunks")
            }

            // Read echo response frame(s)
            val reassembler = BtpReassembler()
            var echoPayload: ByteArray? = null

            while (echoPayload == null) {
                val echoFrame = BtpFrameCodec.readEncryptedFrame(inStream, session.receiveKey)
                if (echoFrame.type == BtpFrameType.DATA) {
                    echoPayload = reassembler.addChunk(echoFrame)
                } else if (echoFrame.type == BtpFrameType.CLOSE) {
                    break
                }
            }

            socket.close()
            return echoPayload
        } catch (e: Exception) {
            Log.e(TAG, "BTP chunked send failed", e)
            onEvent?.invoke("BTP chunked send failed: ${e.message}")
            return null
        }
    }

    /**
     * Self-connects to our own onion address through the SOCKS proxy to verify
     * the descriptor has been published to the HSDirs. Blocks until the
     * descriptor is reachable or the timeout expires.
     *
     * @return true if the self-test succeeded (descriptor is published)
     */
    fun waitForDescriptorUpload(
        timeoutSeconds: Int = 120,
        onProgress: ((String) -> Unit)? = null
    ): Boolean {
        val address = onionAddress ?: return false
        require(socksPort > 0) { "TorManager not started" }

        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        var attempt = 0

        while (System.currentTimeMillis() < deadline) {
            attempt++
            onProgress?.invoke("Self-test attempt $attempt...")
            try {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                val socket = Socket(proxy)
                socket.soTimeout = 30_000
                socket.connect(InetSocketAddress.createUnresolved(address, 80), 30_000)

                // TCP connect succeeded — the descriptor is published and reachable.
                // Close immediately; we don't need to speak any protocol here.
                socket.close()

                Log.i(TAG, "Self-test succeeded on attempt $attempt — descriptor is published")
                onProgress?.invoke("Descriptor published! (TCP connect succeeded)")
                return true
            } catch (e: Exception) {
                Log.i(TAG, "Self-test attempt $attempt failed: ${e.message}")
                onProgress?.invoke("Attempt $attempt: ${e.message}")
                val remaining = deadline - System.currentTimeMillis()
                if (remaining > 10_000) {
                    Thread.sleep(10_000)
                } else if (remaining > 0) {
                    Thread.sleep(remaining)
                }
            }
        }

        Log.w(TAG, "Descriptor not confirmed after $timeoutSeconds seconds")
        onProgress?.invoke("Timed out after $timeoutSeconds seconds")
        return false
    }

    fun stop() {
        val serviceId = onionAddress?.removeSuffix(".onion")
        if (serviceId != null) {
            try {
                boundTorService?.torControlConnection?.delOnion(serviceId)
                Log.i(TAG, "Removed Onion Service $serviceId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove Onion Service", e)
            }
        }
        serviceManager.unbind()
        boundTorService = null
    }
}

/**
 * Fallback stub implementation of Tor transport layer.
 */
class TorManagerStub(private val context: Context) : TorTransport {

    private val torDir = File(context.filesDir, "tor")
    private val hiddenServiceDir = File(torDir, "hidden_service")
    private var controlPort = 9051
    private var socksPort = 9050

    override var onionAddress: String? = null
        private set

    override fun start() {
        if (!torDir.exists()) torDir.mkdirs()
        if (!hiddenServiceDir.exists()) hiddenServiceDir.mkdirs()

        setupTorConfig()
        startDaemon()
        publishOnionService()
    }

    private fun setupTorConfig() {
        val torrc = File(torDir, "torrc")
        torrc.writeText("""
            SocksPort $socksPort
            ControlPort $controlPort
            DataDirectory ${torDir.absolutePath}
            HiddenServiceDir ${hiddenServiceDir.absolutePath}
            HiddenServicePort 80 127.0.0.1:8080
        """.trimIndent())
    }

    private fun startDaemon() {
        // Mocking daemon start
    }

    private fun publishOnionService() {
        val hostnameFile = File(hiddenServiceDir, "hostname")
        if (hostnameFile.exists()) {
            onionAddress = hostnameFile.readText().trim()
        } else {
            onionAddress = "mocked-onion-address.onion"
        }
    }

    override fun connectToPeer(onionHostname: String, port: Int): Socket {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val socket = Socket(proxy)
        socket.connect(InetSocketAddress(onionHostname, port))
        return socket
    }

    override fun listenForPeers(onConnection: (Socket) -> Unit) {
        thread {
            val serverSocket = ServerSocket(8080)
            while (true) {
                val client = serverSocket.accept()
                onConnection(client)
            }
        }
    }
}

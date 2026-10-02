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
        socket.connect(InetSocketAddress(onionHostname, port))
        return socket
    }

    override fun listenForPeers(onConnection: (Socket) -> Unit) {
        thread {
            try {
                val serverSocket = ServerSocket(LOCAL_HS_PORT)
                Log.i(TAG, "Listening for peers on local port $LOCAL_HS_PORT")
                while (true) {
                    val client = serverSocket.accept()
                    onConnection(client)
                }
            } catch (e: IOException) {
                Log.e(TAG, "listenForPeers failed", e)
            }
        }
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

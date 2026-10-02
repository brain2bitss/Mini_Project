package com.miniproject.transport.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Handles Bluetooth RFCOMM and Wi-Fi Direct connections for local mesh networking.
 */
class MeshManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    companion object {
        val BRAMBLE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID
        const val NAME = "BrambleMesh"
    }

    /**
     * Starts listening for incoming Bluetooth connections.
     * // TODO: implement device discovery loop
     * // TODO: handle Android Bluetooth permissions checks and requests
     * // TODO: implement broadcast receivers for Bluetooth state changes
     */
    fun startBluetoothListening(onConnection: (BluetoothSocket) -> Unit) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        thread {
            var serverSocket: BluetoothServerSocket? = null
            try {
                // Note: Missing permissions checks for brevity in MVP
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME, BRAMBLE_UUID)
                while (true) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        onConnection(socket)
                    }
                }
            } catch (e: IOException) {
                // Handle exception
            } finally {
                serverSocket?.close()
            }
        }
    }

    /**
     * Connects to a nearby Bluetooth device.
     */
    fun connectToBluetoothPeer(address: String): BluetoothSocket? {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return null

        try {
            val device = bluetoothAdapter.getRemoteDevice(address)
            val socket = device.createInsecureRfcommSocketToServiceRecord(BRAMBLE_UUID)
            // Cancel discovery before connecting to speed up
            bluetoothAdapter.cancelDiscovery()
            socket.connect()
            return socket
        } catch (e: IOException) {
            return null
        }
    }
}

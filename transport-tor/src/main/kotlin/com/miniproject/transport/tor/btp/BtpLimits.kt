package com.miniproject.transport.tor.btp

object BtpLimits {
    const val PROTOCOL_VERSION: Byte = 1

    /** Tunable max payload per transport layer */
    const val MAX_PAYLOAD_BLUETOOTH = 512       // 512 bytes for RFCOMM / BLE
    const val MAX_PAYLOAD_WIFI_DIRECT = 4096    // 4 KB for Wi-Fi Direct sockets
    const val MAX_PAYLOAD_TOR = 16384           // 16 KB for Tor Onion Service streams

    const val DEFAULT_MAX_PAYLOAD = MAX_PAYLOAD_TOR

    /** Maximum allowed frame wire size (safety limit) */
    const val MAX_FRAME_WIRE_SIZE = 65536       // 64 KB

    /** Default sliding window size for ACK flow control */
    const val DEFAULT_WINDOW_SIZE = 8

    /** Default timeout for incomplete message reassembly (ms) */
    const val DEFAULT_REASSEMBLY_TTL_MS = 60_000L
}

package com.miniproject.transport.tor.btp

enum class BtpFrameType(val code: Byte) {
    DATA(0x01),
    ACK(0x02),
    PING(0x03),
    PONG(0x04),
    REKEY(0x05),
    CLOSE(0x06),
    ERROR(0x07);

    companion object {
        fun fromCode(code: Byte): BtpFrameType =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown BTP frame type code: $code")
    }
}

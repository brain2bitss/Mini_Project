package com.miniproject.transport.tor.btp

import java.util.concurrent.ConcurrentHashMap

class BtpFlowController(
    val windowSize: Int = BtpLimits.DEFAULT_WINDOW_SIZE
) {
    private val inFlight = ConcurrentHashMap<Long, BtpFrame>()

    /**
     * Checks whether a new frame can be transmitted within the sliding window.
     */
    fun canSend(): Boolean = inFlight.size < windowSize

    /**
     * Records a frame as transmitted and in flight awaiting ACK.
     */
    fun onFrameSent(frame: BtpFrame) {
        inFlight[frame.sequenceNumber] = frame
    }

    /**
     * Marks a sequence number as acknowledged by the receiver.
     * @return the acknowledged BtpFrame, or null if not found
     */
    fun onAckReceived(sequenceNumber: Long): BtpFrame? {
        return inFlight.remove(sequenceNumber)
    }

    /**
     * Marks a cumulative ACK or all sequence numbers up to maxSeqNum as acknowledged.
     */
    fun onCumulativeAckReceived(maxSeqNum: Long): List<BtpFrame> {
        val acked = mutableListOf<BtpFrame>()
        val iter = inFlight.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key <= maxSeqNum) {
                acked.add(entry.value)
                iter.remove()
            }
        }
        return acked
    }

    fun inFlightCount(): Int = inFlight.size

    fun isAllAcked(): Boolean = inFlight.isEmpty()

    fun clear() {
        inFlight.clear()
    }
}

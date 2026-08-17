package com.shortscap.app.web.vpn

/**
 * Pure IPv4/IPv6 packet helpers for [LocalVpnService] — parse and build only
 * what the DNS filter needs (UDP datagrams + DNS responses written back into
 * the tunnel). No state, no traffic storage; malformed packets are ignored.
 */
internal object PacketCodec {

    private const val PROTOCOL_UDP = 17

    /** One extracted UDP datagram with the addresses of the IP packet it came in. */
    data class UdpDatagram(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray,
    ) {
        val isIpv6: Boolean get() = srcIp.size == 16
    }

    /**
     * Extracts the UDP datagram from [packet] (up to [length] bytes) when it
     * is an unfragmented IPv4 or IPv6 UDP packet, else null. IPv6 extension
     * headers (hop-by-hop, routing, destination options, AH) are skipped.
     */
    fun parseUdp(packet: ByteArray, length: Int): UdpDatagram? {
        if (length < 20) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        return when (version) {
            4 -> parseIpv4Udp(packet, length)
            6 -> parseIpv6Udp(packet, length)
            else -> null
        }
    }

    private fun parseIpv4Udp(packet: ByteArray, length: Int): UdpDatagram? {
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl) return null
        // Fragments are skipped — DNS queries are never fragmented in practice.
        val fragmentField = ((packet[6].toInt() and 0x1F) shl 8) or (packet[7].toInt() and 0xFF)
        if (fragmentField != 0) return null
        if ((packet[9].toInt() and 0xFF) != PROTOCOL_UDP) return null
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        return extractUdp(packet, length, ihl, srcIp, dstIp)
    }

    private fun parseIpv6Udp(packet: ByteArray, length: Int): UdpDatagram? {
        if (length < 40) return null
        val payloadLen = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        if (payloadLen == 0 || 40 + payloadLen > length) return null // jumbograms unsupported
        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)

        var nextHeader = packet[6].toInt() and 0xFF
        var offset = 40
        var guard = 0
        while (nextHeader != PROTOCOL_UDP && guard < 8) {
            when (nextHeader) {
                0, 43, 60 -> { // hop-by-hop, routing, destination options
                    if (offset + 2 > length) return null
                    val headerLength = (packet[offset + 1].toInt() and 0xFF) * 8 + 8
                    if (offset + headerLength > length) return null
                    nextHeader = packet[offset].toInt() and 0xFF
                    offset += headerLength
                }
                51 -> { // AH
                    if (offset + 2 > length) return null
                    val headerLength = ((packet[offset + 1].toInt() and 0xFF) + 2) * 4
                    if (offset + headerLength > length) return null
                    nextHeader = packet[offset].toInt() and 0xFF
                    offset += headerLength
                }
                else -> return null // fragment header or unknown — skip
            }
            guard++
        }
        if (nextHeader != PROTOCOL_UDP) return null
        return extractUdp(packet, length, offset, srcIp, dstIp)
    }

    private fun extractUdp(packet: ByteArray, length: Int, offset: Int, srcIp: ByteArray, dstIp: ByteArray): UdpDatagram? {
        if (length < offset + 8) return null
        val srcPort = readShort(packet, offset) and 0xFFFF
        val dstPort = readShort(packet, offset + 2) and 0xFFFF
        val udpLen = readShort(packet, offset + 4) and 0xFFFF
        val payloadStart = offset + 8
        if (udpLen < 8 || payloadStart + (udpLen - 8) > length) return null
        val payload = packet.copyOfRange(payloadStart, payloadStart + udpLen - 8)
        return UdpDatagram(srcIp, dstIp, srcPort, dstPort, payload)
    }

    /** Builds an IPv4 UDP packet with a correct IP header checksum. */
    fun buildIpv4Udp(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val packet = ByteArray(totalLen)
        packet[0] = 0x45.toByte() // IPv4, IHL 5
        writeShort(packet, 2, totalLen)
        packet[6] = 0x40.toByte() // Don't Fragment
        packet[8] = 64 // TTL
        packet[9] = PROTOCOL_UDP.toByte()
        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)
        writeShort(packet, 10, onesComplement(packet.copyOfRange(0, 20)))
        writeShort(packet, 20, srcPort)
        writeShort(packet, 22, dstPort)
        writeShort(packet, 24, udpLen)
        // UDP checksum 0 is valid for IPv4.
        System.arraycopy(payload, 0, packet, 28, payload.size)
        return packet
    }

    /**
     * Builds an IPv6 UDP packet with a correct UDP checksum (computed over
     * the IPv6 pseudo-header — required for IPv6, unlike IPv4).
     */
    fun buildIpv6Udp(srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val packet = ByteArray(40 + udpLen)
        packet[0] = 0x60.toByte() // IPv6
        writeShort(packet, 4, udpLen)
        packet[6] = PROTOCOL_UDP.toByte() // next header
        packet[7] = 64 // hop limit
        System.arraycopy(srcIp, 0, packet, 8, 16)
        System.arraycopy(dstIp, 0, packet, 24, 16)
        writeShort(packet, 40, srcPort)
        writeShort(packet, 42, dstPort)
        writeShort(packet, 44, udpLen)
        System.arraycopy(payload, 0, packet, 48, payload.size)

        // UDP checksum over pseudo-header + UDP segment (checksum field zeroed).
        val pseudo = ByteArray(40)
        System.arraycopy(srcIp, 0, pseudo, 0, 16)
        System.arraycopy(dstIp, 0, pseudo, 16, 16)
        writeShort(pseudo, 32, 0)
        writeShort(pseudo, 34, udpLen)
        pseudo[39] = PROTOCOL_UDP.toByte()
        writeShort(packet, 46, onesComplement(pseudo, packet.copyOfRange(40, 40 + udpLen)))
        return packet
    }

    /** Ones-complement checksum over the given byte parts (result is the stored value). */
    fun onesComplement(vararg parts: ByteArray): Int {
        var sum = 0
        for (part in parts) {
            var i = 0
            while (i + 1 < part.size) {
                sum += ((part[i].toInt() and 0xFF) shl 8) or (part[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (i < part.size) sum += (part[i].toInt() and 0xFF) shl 8
            while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum and 0xFFFF).inv() and 0xFFFF
    }

    internal fun readShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    internal fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = ((value ushr 8) and 0xFF).toByte()
        data[offset + 1] = (value and 0xFF).toByte()
    }
}

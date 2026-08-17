package com.shortscap.app.web.vpn

import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketCodecTest {

    private val src4 = byteArrayOf(10, 0, 0, 2)
    private val dst4 = byteArrayOf(8, 8, 8, 8)
    private val src6 = InetAddress.getByName("fd00:1::2").address
    private val dst6 = InetAddress.getByName("2606:4700:4700::1111").address

    @Test
    fun `round-trips an IPv4 UDP datagram`() {
        val payload = "query-bytes".toByteArray()
        val packet = PacketCodec.buildIpv4Udp(src4, dst4, 54321, 53, payload)
        val parsed = PacketCodec.parseUdp(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(54321, parsed!!.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(src4, parsed.srcIp)
        assertArrayEquals(dst4, parsed.dstIp)
        assertArrayEquals(payload, parsed.payload)
        assertEquals(false, parsed.isIpv6)
    }

    @Test
    fun `round-trips an IPv6 UDP datagram`() {
        val payload = "v6-query".toByteArray()
        val packet = PacketCodec.buildIpv6Udp(src6, dst6, 4444, 53, payload)
        val parsed = PacketCodec.parseUdp(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(4444, parsed!!.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(src6, parsed.srcIp)
        assertArrayEquals(dst6, parsed.dstIp)
        assertArrayEquals(payload, parsed.payload)
        assertTrue(parsed.isIpv6)
    }

    @Test
    fun `IPv4 header checksum validates`() {
        // A checksummed header re-folded with the stored checksum included
        // sums to zero — RFC 1071 verification.
        val packet = PacketCodec.buildIpv4Udp(src4, dst4, 1111, 53, byteArrayOf(1, 2, 3))
        assertEquals(0, PacketCodec.onesComplement(packet.copyOfRange(0, 20)))
    }

    @Test
    fun `IPv6 UDP checksum validates over pseudo header`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = PacketCodec.buildIpv6Udp(src6, dst6, 2222, 53, payload)
        val pseudo = ByteArray(40)
        System.arraycopy(src6, 0, pseudo, 0, 16)
        System.arraycopy(dst6, 0, pseudo, 16, 16)
        PacketCodec.writeShort(pseudo, 34, 8 + payload.size)
        pseudo[39] = 17
        val udpSegment = packet.copyOfRange(40, packet.size)
        assertEquals(0, PacketCodec.onesComplement(pseudo, udpSegment))
    }

    @Test
    fun `skips IPv6 extension headers before UDP`() {
        // Base header with next-header = 0 (hop-by-hop).
        val base = ByteArray(40)
        base[0] = 0x60.toByte()
        PacketCodec.writeShort(base, 4, 8 + 8 + payloadSize) // payload: hbh(8) + udp(8 + payload)
        base[6] = 0 // hop-by-hop
        base[7] = 64
        System.arraycopy(src6, 0, base, 8, 16)
        System.arraycopy(dst6, 0, base, 24, 16)
        // Hop-by-hop header: next=UDP(17), hdrExtLen=0 (8 bytes total).
        val hbh = ByteArray(8)
        hbh[0] = 17
        hbh[1] = 0
        // UDP payload.
        val payload = "x".toByteArray()
        val udp = PacketCodec.buildIpv6Udp(src6, dst6, 3333, 53, payload)
        val udpBody = udp.copyOfRange(40, udp.size)

        val packet = ByteArray(40 + hbh.size + udpBody.size)
        System.arraycopy(base, 0, packet, 0, 40)
        System.arraycopy(hbh, 0, packet, 40, hbh.size)
        System.arraycopy(udpBody, 0, packet, 48, udpBody.size)

        val parsed = PacketCodec.parseUdp(packet, packet.size)
        assertNotNull(parsed)
        assertEquals(53, parsed!!.dstPort)
        assertArrayEquals(payload, parsed.payload)
    }

    private val payloadSize = 1

    @Test
    fun `rejects IPv4 fragments`() {
        val packet = PacketCodec.buildIpv4Udp(src4, dst4, 1111, 53, byteArrayOf(1))
        // Set fragment offset != 0 (bytes 6-7: flags(3) + offset(13)).
        packet[6] = 0x00
        packet[7] = 0x29 // offset 5 << 3
        assertNull(PacketCodec.parseUdp(packet, packet.size))
    }

    @Test
    fun `rejects garbage and non-UDP packets`() {
        assertNull(PacketCodec.parseUdp(ByteArray(0), 0))
        assertNull(PacketCodec.parseUdp(ByteArray(19), 19))
        // IPv4 with protocol TCP (6) instead of UDP.
        val tcp = PacketCodec.buildIpv4Udp(src4, dst4, 1, 2, byteArrayOf(1))
        tcp[9] = 6
        assertNull(PacketCodec.parseUdp(tcp, tcp.size))
    }
}

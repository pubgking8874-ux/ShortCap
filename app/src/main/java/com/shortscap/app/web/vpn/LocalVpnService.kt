package com.shortscap.app.web.vpn

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.IBinder
import com.shortscap.app.db.ShortsCapDatabase
import com.shortscap.app.web.domain.BlockedDomainRepository
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * LocalVpnService — the Web Blocking Engine: a [VpnService] that enforces
 * [BlockedDomainRepository] at the DNS **and** IP layer.
 *
 * Enforcement model (strengthened per the Audit 2 bypass report):
 *  - The tunnel routes ONLY the addresses that must be filtered: the DNS
 *    servers (IPv4 **and IPv6**) and the currently-known IPs of blocked
 *    domains. Everything else stays on the normal network path — allowed
 *    websites load exactly as before, with no full-tunnel capture and no TCP
 *    relay.
 *  - DNS (UDP 53, IPv4 + IPv6): parsed by [DnsRequestParser]; a blocked
 *    domain gets NO answer (packet dropped) so it cannot resolve; allowed
 *    queries are forwarded to the real resolver through a protected socket
 *    and the answer is relayed back.
 *  - Direct-IP: any packet (TCP/UDP/QUIC) to a known blocked-domain IP is
 *    dropped, so a previously resolved or cached IP cannot load the site.
 *
 * Known Android limitations (documented, never hidden):
 *  - DoH / DoT / Android Private DNS and queries to alternate resolvers go
 *    outside the tunnel and cannot be intercepted without routing ALL
 *    traffic and running a full TCP relay (out of scope here). On some
 *    Android versions Private DNS can bypass VpnService entirely.
 *  - The blocked-IP table is best-effort: it covers the IPs resolvable at
 *    rule-refresh time (apex + www.); IP changes between refreshes and
 *    exotic subdomains can slip until the next refresh.
 *  - Device/Chrome DNS caches are not purged (no public API); the IP table
 *    mitigates cached IPs by dropping their connections.
 *
 * Privacy: only queried domain names and the addresses of blocked domains
 * are inspected, in memory, never stored or logged. No browsing history.
 */
class LocalVpnService : VpnService() {

    companion object {
        private const val SESSION_NAME = "ShortsCap Local VPN"
        private const val TUN_ADDRESS = "10.0.0.1"
        private const val TUN_IPV6_ADDRESS = "fd00:1::1"
        private const val MTU = 1500
        private const val DNS_PORT = 53
        private const val DNS_TIMEOUT_MS = 5_000
        private const val DNS_PROXY_THREADS = 8
        private const val IP_RESOLVE_TIMEOUT_MS = 2_000
        private const val IP_RESOLVE_BUDGET_MS = 10_000L
        private val DEFAULT_DNS_SERVERS = listOf(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("8.8.8.8"),
        )

        /** True while the tunnel is established. */
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: LocalVpnService? = null

        @Volatile
        private var blockedDomains: Set<String> = emptySet()

        @Volatile
        private var blockedIps: Set<InetAddress> = emptySet()

        /**
         * Reloads the blocked-domain rules (and their IPs, best-effort) from
         * [BlockedDomainRepository]. Suspend version — call from a coroutine,
         * e.g. after a block/unblock while the VPN is running.
         */
        suspend fun syncBlockedDomains(context: Context) {
            val domains = withContext(Dispatchers.IO) {
                val dao = ShortsCapDatabase.getInstance(context.applicationContext).blockedDomainDao()
                BlockedDomainRepository(dao).getAll()
            }
            blockedDomains = domains.mapTo(HashSet()) { it.domain.lowercase() }
            instance?.resolveAndStoreBlockedIps()
        }

        /** Blocking version of [syncBlockedDomains] for non-coroutine callers. */
        fun refreshBlockedDomains(context: Context) {
            runBlocking { syncBlockedDomains(context) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val alive = AtomicBoolean(false)
    private val proxyExecutor = Executors.newFixedThreadPool(DNS_PROXY_THREADS) { r ->
        Thread(r, "shortscap-dns-proxy").apply { isDaemon = true }
    }
    private val dnsFilter = DnsFilter { blockedDomains }

    private var tunFd: ParcelFileDescriptor? = null
    private var tunOut: FileOutputStream? = null
    private var dnsServerV4: InetAddress? = null
    private var dnsServerV6: InetAddress? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        if (tunFd == null) {
            serviceScope.launch { startVpn() }
        } else {
            // Already running — reload the latest rules (domains + IPs).
            serviceScope.launch { refreshRules() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private suspend fun startVpn() {
        if (tunFd != null) return
        // Permission can be revoked while the VPN is up; never re-establish
        // without it.
        if (VpnService.prepare(this) != null) {
            stopSelf()
            return
        }
        // Load rules BEFORE building routes so the blocked-IP routes exist
        // from the first packet.
        refreshRules()
        val dnsServers = resolveDnsServers().ifEmpty { DEFAULT_DNS_SERVERS }
        establishTunnel(dnsServers)
    }

    /** Loads blocked domains + resolves their current IPs (best-effort). */
    private suspend fun refreshRules() {
        blockedDomains = withContext(Dispatchers.IO) {
            val dao = ShortsCapDatabase.getInstance(applicationContext).blockedDomainDao()
            BlockedDomainRepository(dao).getAll().mapTo(HashSet()) { it.domain.lowercase() }
        }
        resolveAndStoreBlockedIps()
    }

    /**
     * Re-resolves the blocked domains to their current IPv4/IPv6 addresses so
     * the engine can route + drop packets to them (covers direct-IP access,
     * QUIC, and cached-IP connections). Best-effort — resolution failures are
     * non-fatal; DNS-level blocking still applies to fresh lookups.
     */
    suspend fun resolveAndStoreBlockedIps() {
        val server = dnsServerV4 ?: resolveDnsServers().firstOrNull { it.address.size == 4 } ?: return
        val domains = blockedDomains
        if (domains.isEmpty()) {
            blockedIps = emptySet()
            return
        }
        blockedIps = withTimeoutOrNull(IP_RESOLVE_BUDGET_MS) {
            coroutineScope {
                domains
                    .flatMap { listOf(it, "www.$it") }
                    .distinct()
                    .map { candidate -> async(Dispatchers.IO) { resolveAddresses(candidate, server) } }
                    .awaitAll()
                    .flatten()
                    .toHashSet()
            }
        } ?: emptySet()
    }

    private fun resolveAddresses(domain: String, server: InetAddress): Set<InetAddress> {
        val out = HashSet<InetAddress>()
        query(domain, 1, server, out) // A
        query(domain, 28, server, out) // AAAA
        return out
    }

    /** One A/AAAA lookup through a PROTECTED socket (bypasses our own tunnel). */
    private fun query(domain: String, type: Int, server: InetAddress, out: MutableSet<InetAddress>) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = IP_RESOLVE_TIMEOUT_MS
            val query = DnsAnswerParser.buildQuery(domain, type)
            socket.send(DatagramPacket(query, query.size, server, DNS_PORT))
            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            out += DnsAnswerParser.parseAddresses(
                response.data.copyOfRange(response.offset, response.offset + response.length),
            )
        } catch (_: SocketTimeoutException) {
            // Best-effort — never fatal.
        } catch (_: IOException) {
        } catch (_: Exception) {
        } finally {
            socket?.close()
        }
    }

    @Synchronized
    private fun establishTunnel(dnsServers: List<InetAddress>) {
        if (tunFd != null) return

        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, 32)
        var hasV6 = false
        dnsServers.forEach { server ->
            builder.addDnsServer(server)
            builder.addRoute(server, if (server.address.size == 4) 32 else 128)
            if (server.address.size == 16) hasV6 = true
        }
        if (hasV6) builder.addAddress(TUN_IPV6_ADDRESS, 128)
        // Route the known blocked-domain IPs so any packet to them (direct-IP,
        // QUIC, cached connections) enters the tunnel and is dropped.
        blockedIps.forEach { ip ->
            builder.addRoute(ip, if (ip.address.size == 4) 32 else 128)
        }

        val fd = builder.establish() ?: run {
            stopSelf()
            return
        }
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)

        tunFd = fd
        tunOut = output
        dnsServerV4 = dnsServers.firstOrNull { it.address.size == 4 }
        dnsServerV6 = dnsServers.firstOrNull { it.address.size == 16 }
        alive.set(true)
        isRunning = true

        Thread({ readLoop(input) }, "shortscap-vpn-reader").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopVpn() {
        alive.set(false)
        isRunning = false
        // Closing the fd unblocks the reader and tears down the tunnel.
        try {
            tunFd?.close()
        } catch (_: IOException) {
        }
        tunFd = null
        tunOut = null
        proxyExecutor.shutdownNow()
    }

    // ------------------------------------------------------------------
    // Tun read loop — DNS filtering + IP-level dropping only.
    // ------------------------------------------------------------------

    private fun readLoop(input: FileInputStream) {
        val buffer = ByteArray(MTU)
        try {
            while (alive.get()) {
                val read = input.read(buffer)
                if (read <= 0) break
                handlePacket(buffer, read)
            }
        } catch (_: IOException) {
            // fd closed during shutdown — expected.
        } catch (_: Exception) {
            // Never let a single bad packet kill the loop.
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int) {
        val udp = PacketCodec.parseUdp(packet, length) ?: return

        if (udp.dstPort == DNS_PORT) {
            handleDns(udp)
            return
        }
        // Non-DNS packet to a known blocked-domain IP (direct-IP / QUIC /
        // cached-IP connection) → drop: the website cannot be reached.
        if (blockedIps.any { it.address.contentEquals(udp.dstIp) }) return
        // Anything else routed by mistake is never forwarded blindly.
    }

    private fun handleDns(udp: PacketCodec.UdpDatagram) {
        val domain = DnsRequestParser.parseDomain(udp.payload) ?: return
        if (dnsFilter.matches(domain)) {
            // BLOCK — drop the packet: no DNS answer, the website never loads.
            return
        }
        // ALLOW — forward to the real resolver and relay the answer back.
        val server = if (udp.isIpv6) dnsServerV6 else dnsServerV4
        if (server == null) return // no matching resolver family configured
        try {
            proxyExecutor.execute { relayDns(udp, server) }
        } catch (_: RejectedExecutionException) {
            // Executor shut down during teardown — drop the query, never crash.
        }
    }

    private fun relayDns(udp: PacketCodec.UdpDatagram, server: InetAddress) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            // Protected socket: the forwarded query must bypass the VPN.
            protect(socket)
            socket.soTimeout = DNS_TIMEOUT_MS
            socket.send(DatagramPacket(udp.payload, udp.payload.size, server, DNS_PORT))

            val buffer = ByteArray(MTU)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)

            val out = tunOut ?: return
            val payload = response.data.copyOfRange(response.offset, response.offset + response.length)
            val ipPacket = if (udp.isIpv6) {
                PacketCodec.buildIpv6Udp(server.address, udp.srcIp, DNS_PORT, udp.srcPort, payload)
            } else {
                PacketCodec.buildIpv4Udp(server.address, udp.srcIp, DNS_PORT, udp.srcPort, payload)
            }
            out.write(ipPacket)
            out.flush()
        } catch (_: SocketTimeoutException) {
            // Resolver timeout — the client retries on its own.
        } catch (_: IOException) {
        } finally {
            socket?.close()
        }
    }

    // ------------------------------------------------------------------
    // DNS server discovery — the network's real DNS servers (IPv4 + IPv6),
    // with public IPv4 fallbacks.
    // ------------------------------------------------------------------

    private fun resolveDnsServers(): List<InetAddress> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        val linkProperties: LinkProperties = cm.getLinkProperties(network) ?: return emptyList()
        return linkProperties.dnsServers.filter { server ->
            val size = server.address.size
            (size == 4 || size == 16) &&
                !server.isLoopbackAddress && !server.isAnyLocalAddress && !server.isLinkLocalAddress
        }
    }
}

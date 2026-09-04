package com.hiktv.viewer.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One device found on the LAN, before credentials are entered. */
data class DiscoveredDevice(
    val host: String,
    val httpPort: Int = 80,
    val model: String = "",
    val serial: String = "",
    val source: String, // "sadp" or "sweep", for diagnostics only
)

/**
 * Finds Hikvision devices on the local network without ever sending
 * credentials: SADP multicast (fast, needs a MulticastLock) and a subnet
 * sweep (slower, works on segmented networks) run concurrently. Both report
 * hits through [onFound] as they arrive rather than waiting to batch results.
 *
 * Credential-free by construction: [subnetSweep] identifies a device purely
 * from the unauthenticated 401 Digest challenge on
 * /ISAPI/System/deviceInfo. Do not add credentials to this probe - they must
 * only ever be sent to the one device the user explicitly selects afterwards.
 */
object Discovery {

    private const val SADP_MULTICAST_ADDR = "239.255.255.250"
    private const val SADP_PORT = 37020
    private const val SADP_TIMEOUT_MS = 3000L
    private val SADP_PROBE_TEMPLATE =
        """<?xml version="1.0" encoding="utf-8"?><Probe><Uuid>%s</Uuid><Types>inquiry</Types></Probe>"""

    private val SWEEP_PORTS = listOf(80, 8000)
    private const val SWEEP_TIMEOUT_MS = 600L
    private const val SWEEP_CONCURRENCY = 64

    suspend fun scan(context: Context, onFound: (DiscoveredDevice) -> Unit) = coroutineScope {
        launch { runCatching { sadpScan(context, onFound) } }
        launch { runCatching { subnetSweep(context, onFound) } }
    }

    // --- SADP multicast --------------------------------------------------

    private suspend fun sadpScan(context: Context, onFound: (DiscoveredDevice) -> Unit) =
        withContext(Dispatchers.IO) {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifiManager?.createMulticastLock("hiktv-sadp")?.apply { acquire() }
            try {
                DatagramSocket(0).use { socket ->
                    socket.broadcast = true
                    val uuid = UUID.randomUUID().toString().uppercase()
                    val probe = SADP_PROBE_TEMPLATE.format(uuid).toByteArray(Charsets.UTF_8)
                    val group = InetAddress.getByName(SADP_MULTICAST_ADDR)
                    socket.send(DatagramPacket(probe, probe.size, group, SADP_PORT))

                    val deadline = System.currentTimeMillis() + SADP_TIMEOUT_MS
                    val buf = ByteArray(4096)
                    while (true) {
                        val remaining = deadline - System.currentTimeMillis()
                        if (remaining <= 0) break
                        socket.soTimeout = remaining.toInt().coerceAtLeast(1)
                        try {
                            val packet = DatagramPacket(buf, buf.size)
                            socket.receive(packet)
                            val xml = String(packet.data, 0, packet.length, Charsets.UTF_8)
                            parseProbeMatch(xml)?.let(onFound)
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                    }
                }
            } finally {
                if (lock?.isHeld == true) lock.release()
            }
        }

    internal fun parseProbeMatch(xml: String): DiscoveredDevice? {
        if (!xml.contains("ProbeMatch")) return null
        val host = extractTag(xml, "IPv4Address")?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
            ?: return null
        return DiscoveredDevice(
            host = host,
            httpPort = extractTag(xml, "HttpPort")?.toIntOrNull() ?: 80,
            model = extractTag(xml, "DeviceDescription") ?: "",
            serial = extractTag(xml, "DeviceSN") ?: "",
            source = "sadp",
        )
    }

    private fun extractTag(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>").find(xml)?.groupValues?.get(1)?.trim()

    // --- Subnet sweep ------------------------------------------------------

    private suspend fun subnetSweep(context: Context, onFound: (DiscoveredDevice) -> Unit) =
        withContext(Dispatchers.IO) {
            val (localAddr, prefixLen) = localIpv4Prefix(context) ?: return@withContext
            val hosts = hostsInSubnet(localAddr, prefixLen)
            if (hosts.isEmpty()) return@withContext

            // No authenticator configured here - this client only ever sends
            // an unauthenticated request and reads the challenge it gets back.
            val client = OkHttpClient.Builder()
                .connectTimeout(SWEEP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(SWEEP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            val semaphore = Semaphore(SWEEP_CONCURRENCY)

            coroutineScope {
                hosts.flatMap { host -> SWEEP_PORTS.map { port -> host to port } }
                    .map { (host, port) ->
                        async {
                            semaphore.withPermit {
                                probeHikvision(client, host, port)?.let(onFound)
                            }
                        }
                    }.awaitAll()
            }
        }

    /** Unauthenticated GET only. Fingerprints from the 401 Digest challenge alone. */
    private fun probeHikvision(client: OkHttpClient, host: String, port: Int): DiscoveredDevice? {
        val req = Request.Builder().url("http://$host:$port/ISAPI/System/deviceInfo").get().build()
        return try {
            client.newCall(req).execute().use { resp ->
                val challenge = resp.header("WWW-Authenticate")
                if (resp.code == 401 && challenge?.contains("Digest", ignoreCase = true) == true) {
                    DiscoveredDevice(host = host, httpPort = port, source = "sweep")
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Uses ConnectivityManager, not WifiManager.getDhcpInfo() - an
     * Ethernet-connected Android TV box has no meaningful Wi-Fi DHCP info.
     */
    private fun localIpv4Prefix(context: Context): Pair<Inet4Address, Int>? {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val linkProperties = cm.getLinkProperties(network) ?: return null
        val linkAddress = linkProperties.linkAddresses
            .firstOrNull { it.address is Inet4Address } ?: return null
        return (linkAddress.address as Inet4Address) to linkAddress.prefixLength
    }

    /** Every usable host address in the local /24 (never wider, even on a larger subnet). */
    internal fun hostsInSubnet(local: Inet4Address, prefixLength: Int): List<String> {
        val effectivePrefix = maxOf(prefixLength, 24)
        val hostBits = 32 - effectivePrefix
        if (hostBits <= 0 || hostBits > 16) return emptyList()
        val localInt = ipToInt(local)
        val networkInt = localInt and (-1 shl hostBits)
        val hostCount = (1 shl hostBits) - 2 // exclude network + broadcast address
        return (1..hostCount).mapNotNull { offset ->
            val candidate = networkInt + offset
            if (candidate == localInt) null else intToIp(candidate)
        }
    }

    private fun ipToInt(addr: Inet4Address): Int {
        val b = addr.address
        return ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
    }

    private fun intToIp(value: Int): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
}

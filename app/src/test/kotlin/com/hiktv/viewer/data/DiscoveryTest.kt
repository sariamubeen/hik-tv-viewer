package com.hiktv.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DiscoveryTest {

    @Test
    fun `hostsInSubnet enumerates a 24 excluding network, broadcast, and self`() {
        val local = InetAddress.getByName("192.168.1.42") as java.net.Inet4Address
        val hosts = Discovery.hostsInSubnet(local, prefixLength = 24)

        assertEquals(253, hosts.size) // 254 usable minus the local address itself
        assertFalse(hosts.contains("192.168.1.0"))   // network address
        assertFalse(hosts.contains("192.168.1.255")) // broadcast address
        assertFalse(hosts.contains("192.168.1.42"))  // local address itself
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
    }

    @Test
    fun `hostsInSubnet never sweeps wider than a 24, even on a larger real subnet`() {
        val local = InetAddress.getByName("10.0.0.5") as java.net.Inet4Address
        val hosts = Discovery.hostsInSubnet(local, prefixLength = 16)

        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("10.0.0.") })
    }

    @Test
    fun `hostsInSubnet respects a real subnet smaller than a 24`() {
        val local = InetAddress.getByName("192.168.1.20") as java.net.Inet4Address
        val hosts = Discovery.hostsInSubnet(local, prefixLength = 28) // 16 addresses, 14 usable

        // Network 192.168.1.16/28: usable range is .17-.30 (network .16, broadcast .31).
        assertEquals(13, hosts.size) // 14 usable minus the local address itself
        val lastOctets = hosts.map { it.substringAfterLast('.').toInt() }
        assertTrue(lastOctets.all { it in 17..30 })
        assertFalse(lastOctets.contains(20)) // local address excluded
    }

    @Test
    fun `parseProbeMatch reads IPv4Address, HttpPort, model, and serial`() {
        // Real SADP reply shape, from the plan's research notes.
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ProbeMatch><Uuid>FC25924E-AFE2-49E6-ACC9-F84A6859054D</Uuid>
            <Types>inquiry</Types>
            <DeviceDescription>DS-2CD2432F-IW</DeviceDescription>
            <DeviceSN>DS-2CD2432F-IW20150126CCCH502126167</DeviceSN>
            <CommandPort>8000</CommandPort>
            <HttpPort>80</HttpPort>
            <IPv4Address>10.1.1.251</IPv4Address>
            </ProbeMatch>
        """.trimIndent()

        val device = Discovery.parseProbeMatch(xml)
        requireNotNull(device)
        assertEquals("10.1.1.251", device.host)
        assertEquals(80, device.httpPort)
        assertEquals("DS-2CD2432F-IW", device.model)
        assertEquals("DS-2CD2432F-IW20150126CCCH502126167", device.serial)
        assertEquals("sadp", device.source)
    }

    @Test
    fun `parseProbeMatch ignores non-ProbeMatch replies and a 0-0-0-0 address`() {
        assertNull(Discovery.parseProbeMatch("<SomethingElse/>"))
        assertNull(Discovery.parseProbeMatch("<ProbeMatch><IPv4Address>0.0.0.0</IPv4Address></ProbeMatch>"))
    }
}

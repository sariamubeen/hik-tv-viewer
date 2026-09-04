package com.hiktv.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The old-install -> multi-device migration path, isolated from Android I/O. */
class SecureStoreMigrationTest {

    @Test
    fun `buildLegacyDevice carries every old flat field onto the new Device`() {
        val device = buildLegacyDevice(
            host = "10.0.0.50",
            port = 80,
            rtspPort = 554,
            username = "live",
            password = "hunter2pass",
            useHttps = false,
            channelsCsv = "1,2,3,4,5,7",
        )

        assertEquals("10.0.0.50:80", device.id)
        assertEquals("10.0.0.50", device.host)
        assertEquals("live", device.username)
        assertEquals("hunter2pass", device.password)
        assertEquals(listOf(1, 2, 3, 4, 5, 7), device.channels.map { it.id })
        assertTrue(device.channels.all { it.name == "Channel ${it.id}" })
    }

    @Test
    fun `buildLegacyDevice tolerates a blank or malformed channel CSV`() {
        assertTrue(buildLegacyDevice("h", 80, 554, "u", "p", false, "").channels.isEmpty())
        assertTrue(buildLegacyDevice("h", 80, 554, "u", "p", false, "a,b,").channels.isEmpty())
    }
}

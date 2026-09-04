package com.hiktv.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** streamId is arithmetic (channel*100 + streamType), not string concatenation. */
class DeviceTest {

    @Test
    fun `streamId matches Hikvision's own formula for single-digit channels`() {
        assertEquals(101, streamId(1, sub = false))
        assertEquals(102, streamId(1, sub = true))
        assertEquals(801, streamId(8, sub = false))
    }

    @Test
    fun `streamId is correct arithmetic once channel numbers reach two digits`() {
        // The classic bug: naive string concat of "17" + "0" + "1" happens to
        // equal the arithmetic result here, but only by coincidence for
        // single-digit stream types - this pins the actual formula.
        assertEquals(1701, streamId(17, sub = false))
        assertEquals(1902, streamId(19, sub = true))
        assertEquals(1001, streamId(10, sub = false))
    }

    @Test
    fun `snapshotUrl requests the substream when sub is true`() {
        val device = Device(id = "d", host = "10.0.0.50", username = "live", password = "hunter2pass")
        assertTrue(device.snapshotUrl(1, sub = true).contains("/Streaming/channels/102/picture"))
        assertTrue(device.snapshotUrl(1, sub = false).contains("/Streaming/channels/101/picture"))
    }

    @Test
    fun `rtspUrl URL-encodes credentials`() {
        val device = Device(id = "d", host = "10.0.0.50", username = "user@x", password = "p@ss word")
        val url = device.rtspUrl(1)
        assertTrue(url.startsWith("rtsp://user%40x:p%40ss+word@10.0.0.50:554/"))
    }
}

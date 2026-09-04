package com.hiktv.viewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Exercises Isapi's pure parsing functions against XML captured from a real
 * Hikvision DVR (DS-7108HGHI-M1, firmware V4.83.614) - see the plan's
 * "Live-verified ground truth" section for how these were obtained.
 */
class IsapiTest {

    private fun fixture(name: String): String =
        File("src/test/resources/isapi/$name").readText()

    @Test
    fun `parseAnalogChannels reads real names, ids, and NO VIDEO as offline`() {
        val channels = Isapi.parseAnalogChannels(fixture("video_inputs_channels.xml"))

        assertEquals(8, channels.size)
        assertEquals("Camera 01", channels.first { it.id == 1 }.name)
        // Confirmed live: a channel renamed down to a bare "1" by whoever
        // configured the DVR - names must be trusted as free text.
        assertEquals("1", channels.first { it.id == 2 }.name)

        // Channels 6 and 8 report resDesc "NO VIDEO" - no camera connected.
        assertFalse(channels.first { it.id == 6 }.online)
        assertFalse(channels.first { it.id == 8 }.online)
        assertTrue(channels.first { it.id == 1 }.online)
    }

    @Test
    fun `parseInputProxyChannels returns empty list, not an error, when no IP cameras are configured`() {
        // This DVR reports size="2" proxy slots but zero InputProxyChannel
        // children - a real device shape the parser must not choke on.
        val channels = Isapi.parseInputProxyChannels(fixture("input_proxy_channels.xml"))
        assertTrue(channels.isEmpty())
    }

    @Test
    fun `parseInputProxyStatus returns empty map for an empty status list`() {
        val status = Isapi.parseInputProxyStatus(fixture("input_proxy_channels_status.xml"))
        assertTrue(status.isEmpty())
    }

    @Test
    fun `parseStreamCodecs reads H265 on both main and sub, and G711ulaw audio`() {
        // Confirmed live: this device's substream is H.265 too, not H.264 -
        // the "substream is usually H.264" assumption does not hold here.
        val codecs = Isapi.parseStreamCodecs(
            mainXml = fixture("streaming_channel_101_main.xml"),
            subXml = fixture("streaming_channel_102_sub.xml"),
        )
        requireNotNull(codecs)
        assertEquals("H.265", codecs.mainCodec)
        assertEquals("H.265", codecs.subCodec)
        assertTrue(codecs.hasAudio)
        assertFalse(codecs.smartCodecEnabled)
    }

    @Test
    fun `parseStreamCodecs handles a channel with audio disabled`() {
        val codecs = Isapi.parseStreamCodecs(
            mainXml = fixture("streaming_channel_601_no_audio.xml"),
            subXml = null,
        )
        requireNotNull(codecs)
        assertEquals("H.265", codecs.mainCodec)
        assertEquals("", codecs.subCodec)
        assertFalse(codecs.hasAudio)
    }

    @Test
    fun `isUnsupported is false for real device responses`() {
        assertFalse(Isapi.isUnsupported(fixture("video_inputs_channels.xml")))
        assertFalse(Isapi.isUnsupported(fixture("streaming_channels.xml")))
        assertFalse(Isapi.isUnsupported(fixture("device_info.xml")))
    }

    @Test
    fun `isUnsupported is true for a ResponseStatus notSupport body`() {
        val notSupported = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ResponseStatus version="1.0" xmlns="http://www.hikvision.com/ver10/XMLSchema">
                <statusCode>4</statusCode>
                <statusString>Invalid Operation</statusString>
                <subStatusCode>notSupport</subStatusCode>
            </ResponseStatus>
        """.trimIndent()
        assertTrue(Isapi.isUnsupported(notSupported))
    }

    @Test
    fun `parsing is namespace-agnostic - isapi org namespace parses identically`() {
        val hikvisionNs = fixture("video_inputs_channels.xml")
        val isapiOrgNs = hikvisionNs.replace(
            "http://www.hikvision.com/ver20/XMLSchema",
            "http://www.isapi.org/ver20/XMLSchema",
        )
        val a = Isapi.parseAnalogChannels(hikvisionNs)
        val b = Isapi.parseAnalogChannels(isapiOrgNs)
        assertEquals(a, b)
    }

    @Test
    fun `parseStreamCodecs returns null for a ResponseStatus body`() {
        val notSupported = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ResponseStatus version="1.0" xmlns="http://www.hikvision.com/ver10/XMLSchema">
                <statusCode>4</statusCode>
                <subStatusCode>invalidOperation</subStatusCode>
            </ResponseStatus>
        """.trimIndent()
        assertNull(Isapi.parseStreamCodecs(notSupported, null))
    }
}

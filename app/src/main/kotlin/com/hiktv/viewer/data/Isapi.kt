package com.hiktv.viewer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** A fresh, namespace-unaware parser - real on both device and JVM unit tests. */
private fun newXmlPullParser(): XmlPullParser = KXmlParser()

/**
 * Enumerates the real channels on a [Device] via ISAPI, instead of assuming a
 * fixed channel list. Endpoints and field names below are confirmed against a
 * live Hikvision DVR (DS-7108HGHI-M1, firmware V4.83.614), not just the spec -
 * see the plan's "Live-verified ground truth" section. The `parse*` functions
 * are pure (XML in, data out) and exposed `internal` so they can be unit
 * tested against captured device responses without a network.
 */
object Isapi {

    suspend fun enumerateChannels(device: Device, client: OkHttpClient): List<Channel> =
        withContext(Dispatchers.IO) {
            // Analog (DVR) and IP-proxy (NVR) channels are independent endpoints;
            // a hybrid device can have both, a pure DVR/NVR only one.
            val analog = fetch(device, client, "/ISAPI/System/Video/inputs/channels")
                ?.let(::parseAnalogChannels) ?: emptyList()
            val ipProxy = fetch(device, client, "/ISAPI/ContentMgmt/InputProxy/channels")
                ?.let(::parseInputProxyChannels) ?: emptyList()
            val proxyStatus = fetch(device, client, "/ISAPI/ContentMgmt/InputProxy/channels/status")
                ?.let(::parseInputProxyStatus) ?: emptyMap()
            val base = (analog + ipProxy).associateBy { it.id }.values.toList()

            // Per-channel codec/audio probes run concurrently - one channel's
            // slow response must not serialize behind the others.
            coroutineScope {
                base.map { ch ->
                    async {
                        val mainXml = fetch(device, client, "/ISAPI/Streaming/channels/${streamId(ch.id, sub = false)}")
                        val subXml = fetch(device, client, "/ISAPI/Streaming/channels/${streamId(ch.id, sub = true)}")
                        val codecs = mainXml?.let { parseStreamCodecs(it, subXml) }
                        ch.copy(
                            online = proxyStatus[ch.id] ?: ch.online,
                            mainCodec = codecs?.mainCodec ?: "",
                            subCodec = codecs?.subCodec ?: "",
                            hasAudio = codecs?.hasAudio ?: false,
                            smartCodecEnabled = codecs?.smartCodecEnabled ?: false,
                        )
                    }
                }.awaitAll()
            }.sortedBy { it.id }
        }

    private fun fetch(device: Device, client: OkHttpClient, path: String): String? =
        runCatching { HikClient.get(device, client, path) }.getOrNull()

    // --- Pure parsing, unit-testable against captured fixtures --------------

    internal fun parseAnalogChannels(xml: String): List<Channel> {
        if (isUnsupported(xml)) return emptyList()
        return parseListItems(xml, "VideoInputChannel").mapNotNull { fields ->
            val id = fields["id"]?.toIntOrNull() ?: return@mapNotNull null
            // A device with no camera on this input reports resDesc "NO VIDEO"
            // (confirmed live) - more direct than subscribing to video-loss events.
            Channel(
                id = id,
                name = fields["name"]?.takeIf { it.isNotBlank() } ?: "Channel $id",
                online = fields["resDesc"] != "NO VIDEO",
                mainCodec = "",
                subCodec = "",
                hasAudio = false,
                smartCodecEnabled = false,
            )
        }
    }

    internal fun parseInputProxyChannels(xml: String): List<Channel> {
        if (isUnsupported(xml)) return emptyList()
        // size="N" can be reported with zero child elements (no IP camera
        // configured on any proxy slot yet) - parseListItems naturally
        // returns an empty list in that case, which is correct, not an error.
        return parseListItems(xml, "InputProxyChannel").mapNotNull { fields ->
            val id = fields["id"]?.toIntOrNull() ?: return@mapNotNull null
            Channel(
                id = id,
                name = fields["name"]?.takeIf { it.isNotBlank() } ?: "Channel $id",
                online = true, // refined by parseInputProxyStatus
                mainCodec = "",
                subCodec = "",
                hasAudio = false,
                smartCodecEnabled = false,
            )
        }
    }

    internal fun parseInputProxyStatus(xml: String): Map<Int, Boolean> {
        if (isUnsupported(xml)) return emptyMap()
        return parseListItems(xml, "InputProxyChannelStatus").mapNotNull { fields ->
            val id = fields["id"]?.toIntOrNull() ?: return@mapNotNull null
            id to (fields["online"]?.toBooleanStrictOrNull() ?: true)
        }.toMap()
    }

    internal data class StreamCodecs(
        val mainCodec: String,
        val subCodec: String,
        val hasAudio: Boolean,
        val smartCodecEnabled: Boolean,
    )

    /** [mainXml] is required (a channel with none is treated as absent); [subXml] may be null. */
    internal fun parseStreamCodecs(mainXml: String, subXml: String?): StreamCodecs? {
        if (isUnsupported(mainXml)) return null
        val mainFields = parseSingleDocumentFields(mainXml)
        val subFields = subXml?.let(::parseSingleDocumentFields)
        return StreamCodecs(
            mainCodec = mainFields["Video/videoCodecType"] ?: "",
            subCodec = subFields?.get("Video/videoCodecType") ?: "",
            hasAudio = mainFields["Audio/enabled"]?.toBooleanStrictOrNull() ?: false,
            smartCodecEnabled = mainFields["Video/SmartCodec/enabled"]?.toBooleanStrictOrNull() ?: false,
        )
    }

    /** A device without a given endpoint replies with a ResponseStatus body, not a 404. */
    internal fun isUnsupported(xml: String): Boolean = rootElementName(xml) == "ResponseStatus"

    // --- Namespace-agnostic XML parsing --------------------------------------
    // FEATURE_PROCESS_NAMESPACES is off by default (never enabled below), so
    // parser.name is always the bare local tag name - deliberately relied on
    // here, since real firmware mixes hikvision.com and isapi.org namespaces.

    private fun rootElementName(xml: String): String? {
        val parser = newXmlPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) return parser.name
            eventType = parser.next()
        }
        return null
    }

    /** Parses `<XList><X>...</X><X>...</X></XList>` into one field-map per `<X>`. */
    private fun parseListItems(xml: String, itemTag: String): List<Map<String, String>> {
        val parser = newXmlPullParser()
        parser.setInput(StringReader(xml))
        val items = mutableListOf<Map<String, String>>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            eventType = if (eventType == XmlPullParser.START_TAG && parser.name == itemTag) {
                items += consumeSubtreeFields(parser)
                parser.next() // consumeSubtreeFields leaves us positioned at itemTag's END_TAG
            } else {
                parser.next()
            }
        }
        return items
    }

    /** Parses a single-root document into one field-map, paths relative to the root. */
    private fun parseSingleDocumentFields(xml: String): Map<String, String> {
        val parser = newXmlPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.START_TAG) {
            if (eventType == XmlPullParser.END_DOCUMENT) return emptyMap()
            eventType = parser.next()
        }
        return consumeSubtreeFields(parser)
    }

    /**
     * Parser must be positioned at a START_TAG. Consumes through the matching
     * END_TAG and returns every leaf's text, keyed by its path (element names
     * joined with "/") relative to that start tag - e.g. "Video/videoCodecType".
     * Leaves the parser positioned at the matching END_TAG.
     */
    private fun consumeSubtreeFields(parser: XmlPullParser): Map<String, String> {
        val rootDepth = parser.depth
        val path = ArrayDeque<String>()
        val fields = mutableMapOf<String, String>()
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == rootDepth)) {
            when (eventType) {
                XmlPullParser.START_TAG -> path.addLast(parser.name)
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (!text.isNullOrEmpty() && path.isNotEmpty()) {
                        fields[path.joinToString("/")] = text
                    }
                }
                XmlPullParser.END_TAG -> if (path.isNotEmpty()) path.removeLast()
            }
            eventType = parser.next()
        }
        return fields
    }
}

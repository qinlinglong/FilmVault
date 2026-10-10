package com.filmvault.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser

data class DlnaRenderer(
    val name: String,
    val location: String,
    val controlUrl: String,
)

/** 通用 DLNA/UPnP 投屏客户端。只发送资源地址到电视盒子，不传输手机画面。 */
object DlnaCastClient {
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "upnp:rootdevice",
        "ssdp:all",
    )
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()
    private val soapType = "text/xml; charset=utf-8".toMediaType()

    suspend fun discover(context: Context): List<DlnaRenderer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("filmvault-dlna")?.apply { setReferenceCounted(false); acquire() }
        val locations = linkedSetOf<String>()
        val wifiNetwork = findWifiNetwork(context)
        try {
            createDiscoverySocket(context, wifiNetwork).use { socket ->
                // Android 在移动网络、VPN 和 Wi-Fi 同时存在时可能把 UDP 从错误接口
                // 发出。显式绑定 Wi-Fi，确保 SSDP 广播真的进入当前局域网。
                socket.soTimeout = 300
                val address = InetAddress.getByName(SSDP_ADDRESS)
                SEARCH_TARGETS.forEach { target ->
                    val query = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: $target\r\n\r\n"
                    val bytes = query.toByteArray(StandardCharsets.UTF_8)
                    repeat(2) {
                        socket.send(DatagramPacket(bytes, bytes.size, address, SSDP_PORT))
                    }
                }

                val deadline = android.os.SystemClock.elapsedRealtime() + 3_200L
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    try {
                        val buffer = ByteArray(8192)
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)
                        val headers = response.data.decodeToString(0, response.length)
                            .lineSequence().mapNotNull { line ->
                                line.indexOf(':').takeIf { it > 0 }?.let { i ->
                                    line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
                                }
                            }.toMap()
                        headers["location"]
                            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            ?.let(locations::add)
                    } catch (_: java.net.SocketTimeoutException) {
                        // Keep listening until the fixed discovery deadline.
                    }
                }
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }

        // First finish the bounded UDP scan, then resolve device descriptions in
        // parallel. A dead device can no longer block receipt of other replies.
        val renderers = coroutineScope {
            locations.take(32).map { location ->
                async { fetchRenderer(location) }
            }.awaitAll()
                .filterNotNull()
                .distinctBy { it.controlUrl }
                .sortedBy { it.name.lowercase() }
        }
        if (renderers.isEmpty() && locations.isNotEmpty()) {
            error("收到 ${locations.size} 个局域网设备响应，但未发现兼容的 DLNA 播放服务；电视可能使用厂商投屏、乐播或 Miracast 协议")
        }
        renderers
    }

    private fun findWifiNetwork(context: Context): Network? {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return null
        return connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun createDiscoverySocket(context: Context, wifiNetwork: Network?): MulticastSocket {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val interfaceName = wifiNetwork?.let { connectivity?.getLinkProperties(it)?.interfaceName }
        val networkInterface = interfaceName?.let(NetworkInterface::getByName)
        val socket = MulticastSocket(null).apply { reuseAddress = true }

        // Listen on the standard SSDP port as well as sending M-SEARCH. Some
        // receivers primarily announce themselves through NOTIFY ssdp:alive and
        // do not reliably answer searches from an ephemeral UDP socket.
        runCatching { socket.bind(InetSocketAddress(SSDP_PORT)) }
            .recoverCatching { socket.bind(InetSocketAddress(0)) }
            .getOrElse {
                socket.close()
                throw it
            }
        wifiNetwork?.bindSocket(socket)
        if (networkInterface != null) {
            runCatching {
                socket.joinGroup(InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT), networkInterface)
            }
        }
        return socket
    }

    suspend fun play(renderer: DlnaRenderer, mediaUrl: String, title: String) = withContext(Dispatchers.IO) {
        soap(renderer, "SetAVTransportURI", """
            <InstanceID>0</InstanceID>
            <CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>
            <CurrentURIMetaData>${xmlEscape(didl(mediaUrl, title))}</CurrentURIMetaData>
        """.trimIndent())
        soap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    suspend fun stop(renderer: DlnaRenderer) = withContext(Dispatchers.IO) {
        soap(renderer, "Stop", "<InstanceID>0</InstanceID>")
    }

    private fun fetchRenderer(location: String): DlnaRenderer? = runCatching {
        val response = http.newCall(Request.Builder().url(location).build()).execute()
        response.use {
            if (!it.isSuccessful) return null
            val xml = it.body?.string().orEmpty()
            val description = parseDescription(xml) ?: return null
            val base = description.urlBase ?: location
            val name = description.friendlyName ?: URI(location).host ?: "DLNA 播放器"
            DlnaRenderer(name, location, URI(base).resolve(description.controlUrl).toString())
        }
    }.getOrNull()

    private data class DeviceDescription(
        val friendlyName: String?,
        val urlBase: String?,
        val controlUrl: String,
    )

    /** Parse UPnP XML by local tag name, including documents using namespace prefixes. */
    private fun parseDescription(xml: String): DeviceDescription? = runCatching {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        var friendlyName: String? = null
        var urlBase: String? = null
        var serviceDepth = -1
        var serviceType: String? = null
        var controlUrl: String? = null
        var avTransportControlUrl: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "friendlyname" -> if (friendlyName == null) friendlyName = parser.nextText().trim()
                    "urlbase" -> if (urlBase == null) urlBase = parser.nextText().trim()
                    "service" -> {
                        serviceDepth = parser.depth
                        serviceType = null
                        controlUrl = null
                    }
                    "servicetype" -> if (serviceDepth >= 0) serviceType = parser.nextText().trim()
                    "controlurl" -> if (serviceDepth >= 0) controlUrl = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (
                    parser.name.equals("service", ignoreCase = true) && parser.depth == serviceDepth
                ) {
                    if (serviceType?.contains("AVTransport", ignoreCase = true) == true && !controlUrl.isNullOrBlank()) {
                        avTransportControlUrl = controlUrl
                    }
                    serviceDepth = -1
                }
            }
            event = parser.next()
        }
        avTransportControlUrl?.let { DeviceDescription(friendlyName, urlBase, it) }
    }.getOrNull()

    private fun soap(renderer: DlnaRenderer, action: String, body: String) {
        val envelope = """<?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body><u:$action xmlns:u="$AV_TRANSPORT">$body</u:$action></s:Body>
            </s:Envelope>""".trimIndent()
        http.newCall(
            Request.Builder().url(renderer.controlUrl)
                .post(envelope.toRequestBody(soapType))
                .header("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) error("DLNA ${response.code}")
        }
    }

    private fun didl(url: String, title: String): String = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>${xmlEscape(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:*:*">${xmlEscape(url)}</res></item></DIDL-Lite>"""

    private fun xmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}

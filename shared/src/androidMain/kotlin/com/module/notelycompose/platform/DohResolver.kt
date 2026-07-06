package com.module.notelycompose.platform

import com.module.notelycompose.core.debugPrintln
import java.net.HttpURLConnection
import java.net.URL

/**
 * DNS-over-HTTPS-Fallback für Umgebungen mit defektem/filterndem lokalem DNS.
 *
 * Hintergrund: Manche Router-DNS liefern für CDN-Hosts (beobachtet mit `*.hf.co`)
 * Null-Routen (0.0.0.0/::) — der Modelldownload scheitert dann, obwohl das Netz
 * grundsätzlich funktioniert. Dieser Resolver fragt die DoH-Server von Google und
 * Cloudflare direkt über ihre IP an (die TLS-Zertifikate von dns.google und
 * cloudflare-dns.com enthalten die IPs 8.8.8.8 bzw. 1.1.1.1 als SAN, daher braucht
 * der Fallback selbst KEINE funktionierende DNS-Auflösung).
 */
object DohResolver {

    // %s = Hostname. Google zuerst (IP-SAN im Zertifikat), Cloudflare als Reserve.
    private val ENDPOINTS = listOf(
        "https://8.8.8.8/resolve?name=%s&type=A",
        "https://1.1.1.1/dns-query?name=%s&type=A"
    )

    private const val TIMEOUT_MS = 10_000

    /** Löst [host] via DoH auf. Leere Liste wenn kein Endpunkt eine Antwort liefert. */
    fun resolve(host: String): List<String> {
        for (endpoint in ENDPOINTS) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint.replace("%s", host)).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/dns-json")
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = connection.inputStream.use { it.readBytes().decodeToString() }
                    val ips = parseDohAnswers(json)
                    if (ips.isNotEmpty()) {
                        debugPrintln { "DoH: $host -> $ips (via $endpoint)" }
                        return ips
                    }
                }
            } catch (e: Exception) {
                debugPrintln { "DoH: Endpunkt $endpoint fehlgeschlagen: ${e.message}" }
            } finally {
                connection?.disconnect()
            }
        }
        return emptyList()
    }

    /**
     * Extrahiert die IPv4-Adressen aus einer dns-json-Antwort
     * (`{"Answer":[{"type":1,"data":"1.2.3.4"},...]}`). Bewusst ohne JSON-Bibliothek:
     * Das IPv4-Muster matcht nur A-Records — CNAME-Einträge (type 5, data=Domainname)
     * fallen automatisch heraus. Pure Funktion, unit-getestet.
     */
    fun parseDohAnswers(json: String): List<String> {
        val ipv4InData = Regex("\"data\"\\s*:\\s*\"((?:\\d{1,3}\\.){3}\\d{1,3})\"")
        return ipv4InData.findAll(json)
            .map { it.groupValues[1] }
            .filter { ip -> ip.split(".").all { octet -> octet.toInt() in 0..255 } }
            .distinct()
            .toList()
    }
}

package com.junkfood.seal.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object PlatformResilienceUtil {

    private const val TAG = "PlatformResilienceUtil"

    private val MODERN_USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
        "Mozilla/5.0 (Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0"
    )

    /**
     * Returns a random modern User-Agent string to prevent browser fingerprinting.
     */
    fun getRandomUserAgent(): String {
        return MODERN_USER_AGENTS.random()
    }

    /**
     * Checks proxy connectivity and returns latency in milliseconds.
     * Supports formats: "http://host:port", "socks5://host:port", "host:port"
     */
    suspend fun checkProxyHealth(proxyUrl: String, testUrl: String = "https://www.google.com/generate_204"): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanUrl = proxyUrl.trim()
                if (cleanUrl.isEmpty()) {
                    throw IllegalArgumentException("Proxy URL cannot be empty")
                }

                val isSocks = cleanUrl.startsWith("socks", ignoreCase = true)
                val strippedUrl = cleanUrl.replace(Regex("^(https?|socks5?|socks4?)://", RegexOption.IGNORE_CASE), "")
                val parts = strippedUrl.split(":")
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid proxy format. Expected host:port")
                }

                val host = parts[0]
                val port = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid proxy port")

                val proxyType = if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP
                val proxy = Proxy(proxyType, InetSocketAddress(host, port))

                val startTime = System.currentTimeMillis()
                val connection = (URL(testUrl).openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", getRandomUserAgent())
                }

                val responseCode = connection.responseCode
                val latency = System.currentTimeMillis() - startTime
                connection.disconnect()

                if (responseCode in 200..399) {
                    Log.d(TAG, "Proxy $cleanUrl reachable. Latency: ${latency}ms")
                    latency
                } else {
                    throw IllegalStateException("Proxy returned HTTP $responseCode")
                }
            }
        }

    /**
     * Validates if a cookie string contains essential authentication tokens for YouTube/Google.
     */
    fun hasValidYouTubeAuth(cookiesContent: String): Boolean {
        val hasSapisid = cookiesContent.contains("SAPISID", ignoreCase = true) || cookiesContent.contains("__Secure-3PAPISID", ignoreCase = true)
        val hasSsid = cookiesContent.contains("SSID", ignoreCase = true) || cookiesContent.contains("LOGIN_INFO", ignoreCase = true)
        return hasSapisid && hasSsid
    }
}

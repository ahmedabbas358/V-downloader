package com.junkfood.seal.download.engine.builder

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.webkit.CookieManager
import androidx.annotation.CheckResult
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.ui.page.settings.network.Cookie
import com.junkfood.seal.util.FileUtil.getCookiesFile
import com.junkfood.seal.util.PreferenceUtil.COOKIE_HEADER
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * NetworkOptionBuilder
 *
 * Handles network configuration, cookie extraction from WebView SQLite database,
 * proxy routing, user-agent injection, aria2c acceleration, and connection resilience.
 */
object NetworkOptionBuilder {

    object CookieScheme {
        const val NAME = "name"
        const val VALUE = "value"
        const val SECURE = "is_secure"
        const val EXPIRY = "expires_utc"
        const val HOST = "host_key"
        const val PATH = "path"
    }

    const val MODERN_BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Applies cookie options to a yt-dlp request.
     */
    fun applyCookies(
        request: YoutubeDLRequest,
        userAgentString: String = "",
        appContext: Context = context,
    ): YoutubeDLRequest = request.apply {
        val cookiesFile = appContext.getCookiesFile()
        if (cookiesFile.exists() && cookiesFile.length() > 0) {
            addOption("--cookies", cookiesFile.absolutePath)
        }
        val ua = userAgentString.ifBlank { MODERN_BROWSER_USER_AGENT }
        addOption("--user-agent", ua)
    }

    /**
     * Applies platform-specific extractor arguments to prevent bot detection and API failures
     * for Instagram, Twitter/X, TikTok, and YouTube.
     */
    fun applySocialMediaOptions(
        request: YoutubeDLRequest,
        url: String,
        userAgentString: String = "",
        appContext: Context = context,
    ): YoutubeDLRequest = request.apply {
        val ua = userAgentString.ifBlank { MODERN_BROWSER_USER_AGENT }
        addOption("--user-agent", ua)
        addOption("--add-header", "Accept-Language: en-US,en;q=0.9,ar;q=0.8")
        addOption("--add-header", "Sec-Fetch-Mode: navigate")

        val lowerUrl = url.lowercase()
        when {
            lowerUrl.contains("x.com") || lowerUrl.contains("twitter.com") -> {
                addOption("--extractor-args", "twitter:api=syndication")
            }
            lowerUrl.contains("instagram.com") || lowerUrl.contains("instagr.am") -> {
                addOption("--extractor-args", "instagram:api=graphql")
            }
            lowerUrl.contains("tiktok.com") -> {
                addOption("--extractor-args", "tiktok:app_version=35.1.3")
            }
            lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> {
                addOption("--extractor-args", "youtube:player_client=android,web;player_skip=configs")
            }
        }

        // Automatically attach cookies if available in app storage
        val cookiesFile = appContext.getCookiesFile()
        if (cookiesFile.exists() && cookiesFile.length() > 10) {
            addOption("--cookies", cookiesFile.absolutePath)
        }
    }

    /**
     * Applies proxy configuration.
     */
    fun applyProxy(
        request: YoutubeDLRequest,
        proxyUrl: String,
    ): YoutubeDLRequest = request.apply {
        if (proxyUrl.isNotBlank()) {
            addOption("--proxy", proxyUrl)
        }
    }

    /**
     * Applies aria2c downloader or fragment concurrency.
     */
    fun applyDownloaderAcceleration(
        request: YoutubeDLRequest,
        useAria2c: Boolean,
        concurrentFragments: Int,
    ): YoutubeDLRequest = request.apply {
        if (useAria2c) {
            addOption("--downloader", "libaria2c.so")
        } else if (concurrentFragments > 1) {
            addOption("--concurrent-fragments", concurrentFragments)
        }
    }

    /**
     * Applies standard network resilience options to prevent connection drops,
     * transient extractor errors, and SSL certificate handshake issues.
     */
    fun applyNetworkResilience(
        request: YoutubeDLRequest,
        forceIpv4: Boolean = false,
        debug: Boolean = false,
    ): YoutubeDLRequest = request.apply {
        addOption("-R", "10")
        addOption("--retries", "10")
        addOption("--fragment-retries", "10")
        addOption("--file-access-retries", "5")
        addOption("--socket-timeout", "25")
        addOption("--no-check-certificates")
        addOption("--geo-bypass")

        if (forceIpv4) {
            addOption("-4")
        }
        if (debug) {
            addOption("-v")
        }
    }

    @CheckResult
    fun getCookieListFromDatabase(): Result<List<Cookie>> = runCatching {
        CookieManager.getInstance().run {
            if (!hasCookies()) throw Exception("There are no cookies in the database!")
            flush()
        }
        SQLiteDatabase.openDatabase(
            context.dataDir.resolve("app_webview/Default/Cookies").absolutePath,
            null,
            OPEN_READONLY,
        ).use { db ->
            val projection = arrayOf(
                CookieScheme.HOST,
                CookieScheme.EXPIRY,
                CookieScheme.PATH,
                CookieScheme.NAME,
                CookieScheme.VALUE,
                CookieScheme.SECURE,
            )
            val cookieList = mutableListOf<Cookie>()
            db.query("cookies", projection, null, null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val expiry = cursor.getLong(cursor.getColumnIndexOrThrow(CookieScheme.EXPIRY))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(CookieScheme.NAME))
                    val value = cursor.getString(cursor.getColumnIndexOrThrow(CookieScheme.VALUE))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(CookieScheme.PATH))
                    val secure = cursor.getLong(cursor.getColumnIndexOrThrow(CookieScheme.SECURE)) == 1L
                    val hostKey = cursor.getString(cursor.getColumnIndexOrThrow(CookieScheme.HOST))

                    val host = if (hostKey.isNotEmpty() && hostKey[0] != '.') ".$hostKey" else hostKey
                    cookieList.add(
                        Cookie(
                            domain = host,
                            name = name,
                            value = value,
                            path = path,
                            secure = secure,
                            expiry = expiry,
                        )
                    )
                }
            }
            cookieList
        }
    }

    fun List<Cookie>.toCookiesFileContent(): String =
        this.fold(StringBuilder(COOKIE_HEADER)) { acc, cookie ->
            acc.append(cookie.toNetscapeCookieString()).append("\n")
        }.toString()

    fun getCookiesContentFromDatabase(): Result<String> =
        getCookieListFromDatabase().mapCatching { it.toCookiesFileContent() }
}

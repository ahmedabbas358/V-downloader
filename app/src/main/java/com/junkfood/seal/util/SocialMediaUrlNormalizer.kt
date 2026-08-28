package com.junkfood.seal.util

import android.net.Uri
import android.util.Log
import java.net.URI
import java.util.regex.Pattern

/**
 * SocialMediaUrlNormalizer
 *
 * Provides specialized extraction, sanitization, canonical routing,
 * and tracking-parameter removal for all social media platforms:
 * - Instagram (Reels, Posts, Carousels, Stories, Share links, Threads)
 * - TikTok (Standard, vm.tiktok.com, vt.tiktok.com, t/ shortlinks)
 * - Facebook (Reels, Watch, Share links fb.watch, fb.com)
 * - Twitter / X (x.com, twitter.com, mobile.twitter.com)
 * - YouTube (Shorts, Watch, youtu.be, live)
 * - Pinterest, Snapchat, Reddit, LinkedIn, Douyin, Bilibili
 */
object SocialMediaUrlNormalizer {

    private const val TAG = "SocialMediaUrlNormalizer"

    private val TRACKING_PARAMS = setOf(
        "igsh", "si", "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "mibextid", "feature", "s", "t", "ref", "ref_src", "app", "context",
        "share_id", "xmt", "is_from_webapp", "sender_device", "sender_web_id",
        "rdid", "share_url", "sub_confirmation"
    )

    private val URL_REGEX = Pattern.compile(
        """(https?://[^\s<>"'{}|\\^`]+)""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts all valid HTTP/HTTPS URLs from any text blob (e.g. copied share text with captions,
     * hashtags, Arabic text, emojis), cleans and normalizes them.
     */
    fun extractAndNormalizeUrls(input: String, firstMatchOnly: Boolean = false): List<String> {
        if (input.isBlank()) return emptyList()
        val matcher = URL_REGEX.matcher(input)
        val list = mutableListOf<String>()

        while (matcher.find()) {
            val raw = matcher.group(1).orEmpty()
            val normalized = normalizeUrl(raw)
            if (normalized.isNotBlank()) {
                list.add(normalized)
                if (firstMatchOnly) break
            }
        }
        return list
    }

    /**
     * Normalizes a social media URL into a clean canonical format that yt-dlp extractors can handle natively.
     */
    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isBlank()) return url

        // Remove trailing punctuation or brackets common when copied from chat messages
        url = url.trimEnd('.', ',', '!', ';', ':', ')', ']', '}', '>', '"', '\'')
        url = url.trimStart('(', '[', '{', '<', '"', '\'')

        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host?.lowercase() ?: return cleanTrackingQueryParamsFallback(url)
            var path = uri.path ?: ""
            val rawQuery = uri.rawQuery

            // 1. Instagram Normalization
            if (host.contains("instagram.com") || host == "instagr.am" || host.contains("ddinstagram.com")) {
                // Route normalization
                path = path.replace("/share/reel/", "/reel/")
                    .replace("/share/p/", "/p/")
                    .replace("/reels/", "/reel/")
                val canonicalHost = "www.instagram.com"
                val cleanQuery = cleanQueryString(rawQuery)
                val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
                return "$scheme://$canonicalHost$path$queryPart"
            }

            // 2. Facebook Normalization
            if (host.contains("facebook.com") || host == "fb.watch" || host == "fb.com" || host == "m.facebook.com") {
                path = path.replace("/share/r/", "/reel/")
                    .replace("/share/v/", "/watch/?v=")
                val canonicalHost = if (host == "fb.watch") "fb.watch" else "www.facebook.com"
                val cleanQuery = cleanQueryString(rawQuery)
                val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
                return "$scheme://$canonicalHost$path$queryPart"
            }

            // 3. TikTok Normalization
            if (host.contains("tiktok.com")) {
                val cleanQuery = cleanQueryString(rawQuery)
                val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
                return "$scheme://$host$path$queryPart"
            }

            // 4. Twitter / X Normalization
            if (host == "x.com" || host.contains("twitter.com")) {
                val cleanQuery = cleanQueryString(rawQuery)
                val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
                return "$scheme://$host$path$queryPart"
            }

            // 5. Threads Normalization
            if (host.contains("threads.net")) {
                val cleanQuery = cleanQueryString(rawQuery)
                val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
                return "$scheme://$host$path$queryPart"
            }

            // 6. YouTube Normalization
            if (host.contains("youtube.com") || host == "youtu.be") {
                val isWatchVideo = path.contains("/watch") || host == "youtu.be" || path.contains("/shorts/")
                val cleanQuery = cleanQueryString(rawQuery, stripListParam = isWatchVideo)
                val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
                val canonicalHost = if (host == "youtu.be") "youtu.be" else "www.youtube.com"
                return "$scheme://$canonicalHost$path$queryPart"
            }

            // 7. Generic cleaning
            val cleanQuery = cleanQueryString(rawQuery)
            val queryPart = if (cleanQuery.isNotEmpty()) "?$cleanQuery" else ""
            "$scheme://$host$path$queryPart"
        } catch (e: Exception) {
            Log.w(TAG, "URI parsing failed for $url, falling back to regex cleaner: ${e.message}")
            cleanTrackingQueryParamsFallback(url)
        }
    }

    private fun cleanQueryString(rawQuery: String?, stripListParam: Boolean = false): String {
        if (rawQuery.isNullOrBlank()) return ""
        return rawQuery.split("&")
            .filter { param ->
                if (param.isBlank()) return@filter false
                val key = param.substringBefore("=").lowercase().trim()
                if (stripListParam && (key == "list" || key == "index" || key == "pp")) return@filter false
                key !in TRACKING_PARAMS && !key.startsWith("utm_")
            }
            .joinToString("&")
    }

    private fun cleanTrackingQueryParamsFallback(url: String): String {
        val qIndex = url.indexOf('?')
        if (qIndex == -1) return url
        val base = url.substring(0, qIndex)
        val query = url.substring(qIndex + 1)
        val cleanQuery = cleanQueryString(query)
        return if (cleanQuery.isNotEmpty()) "$base?$cleanQuery" else base
    }
}

package com.junkfood.seal.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object CobaltEngine {
    private const val TAG = "CobaltEngine"
    // Using a public instance or the official one if headers are provided
    private const val API_URL = "https://api.cobalt.tools/api/json"

    suspend fun fetchVideoUrl(videoUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 15000 // 15 seconds
            connection.requestMethod = "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            
            // Required for some Cobalt instances to accept requests from apps
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            
            connection.doOutput = true

            val jsonParam = JSONObject()
            jsonParam.put("url", videoUrl)
            jsonParam.put("vQuality", "1080")
            jsonParam.put("isAudioOnly", false)

            connection.outputStream.use { os ->
                val input = jsonParam.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = InputStreamReader(connection.inputStream)
                val responseBody = reader.readText()
                val responseJson = JSONObject(responseBody)
                
                val status = responseJson.optString("status")
                return@withContext if (status == "redirect" || status == "stream" || status == "success") {
                    responseJson.optString("url")
                } else if (status == "picker") {
                    // It's a carousel, pick the first video
                    val pickerArray = responseJson.optJSONArray("picker")
                    if (pickerArray != null && pickerArray.length() > 0) {
                        pickerArray.getJSONObject(0).optString("url")
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                Log.e(TAG, "Cobalt API failed with response code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in CobaltEngine", e)
            null
        }
    }

    suspend fun fetchFileSize(videoUrl: String): Double = withContext(Dispatchers.IO) {
        try {
            val url = URL(videoUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val contentLength = connection.getHeaderField("Content-Length")
                if (!contentLength.isNullOrEmpty()) {
                    return@withContext contentLength.toDouble()
                }
            }
            0.0
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching file size", e)
            0.0
        }
    }
}

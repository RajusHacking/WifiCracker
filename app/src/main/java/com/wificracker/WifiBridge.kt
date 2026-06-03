package com.eni.wificracker

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WifiBridge(private val ctx: Context, private val web: WebView) {

    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun scan(): String {
        if (!wm.isWifiEnabled) wm.isWifiEnabled = true
        wm.startScan()
        val list = wm.scanResults ?: emptyList()
        val arr = JSONArray()
        list.sortedByDescending { it.level }.forEach { r ->
            val o = JSONObject()
            o.put("ssid", r.SSID.ifBlank { "<hidden>" })
            o.put("bssid", r.BSSID)
            o.put("level", r.level)
            o.put("freq", r.frequency)
            o.put("caps", r.capabilities)
            o.put("security", securityOf(r))
            arr.put(o)
        }
        return arr.toString()
    }

    private fun securityOf(r: ScanResult): String {
        val c = r.capabilities
        return when {
            c.contains("WPA3") -> "WPA3"
            c.contains("WPA2") -> "WPA2"
            c.contains("WPA")  -> "WPA"
            c.contains("WEP")  -> "WEP"
            c.contains("WPS")  -> "WPS"
            else -> "OPEN"
        }
    }

    @JavascriptInterface
    fun crackNetwork(ssid: String, bssid: String, security: String) {
        CoroutineScope(Dispatchers.IO).launch {
            push("status", "Starting attack on $ssid ($security)")

            // 1. Default key guesses based on BSSID OUI
            val defaults = DefaultKeyGen.generate(ssid, bssid)
            push("phase", "Default key generation: ${defaults.size} candidates")
            defaults.forEach { tryKey(ssid, it, "default-key") }

            // 2. WPS PIN guesses (algorithmic)
            val pins = WpsPinGen.pinsFor(bssid)
            push("phase", "WPS PIN attack: ${pins.size} pins")
            pins.forEach { tryKey(ssid, it, "wps-pin") }

            // 3. Dictionary attack (common passwords)
            push("phase", "Dictionary attack: ${CommonPasswords.list.size} passwords")
            CommonPasswords.list.forEach { tryKey(ssid, it, "dictionary") }

            // 4. SSID-derived guesses
            val ssidGuesses = listOf(
                ssid, "$ssid 123", "$ssid@123", "${ssid}1234",
                ssid.lowercase(), ssid.uppercase(), "${ssid}2024", "${ssid}2025"
            )
            push("phase", "SSID-derived: ${ssidGuesses.size}")
            ssidGuesses.forEach { tryKey(ssid, it, "ssid-derived") }

            push("done", "Attack complete on $ssid")
        }
    }

    private suspend fun tryKey(ssid: String, key: String, source: String) {
        withContext(Dispatchers.IO) {
            push("try", JSONObject().apply {
                put("ssid", ssid); put("key", key); put("source", source)
            }.toString())
            // Connection attempt would go here via WifiNetworkSuggestion API.
            // On Android 11+ direct WifiConfiguration is deprecated.
            Thread.sleep(40)
        }
    }

    @JavascriptInterface
    fun getRootPasswords(): String {
        return RootWifi.dump()
    }

    @JavascriptInterface
    fun isRooted(): Boolean = RootWifi.isRooted()

    private fun push(event: String, data: String) {
        val safe = data.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        main.post {
            web.evaluateJavascript("window.onCrackEvent && onCrackEvent('$event','$safe');", null)
        }
    }
}
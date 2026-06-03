package com.eni.wificracker

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootWifi {

    fun isRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            p.waitFor() == 0
        } catch (_: Exception) { false }
    }

    fun dump(): String {
        if (!isRooted()) return JSONObject().put("error", "not rooted").toString()

        val paths = listOf(
            "/data/misc/wifi/WifiConfigStore.xml",
            "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
            "/data/misc/wifi/wpa_supplicant.conf"
        )

        val out = JSONArray()
        for (path in paths) {
            val content = runSu("cat $path") ?: continue
            val nets = parseConfigStore(content)
            nets.forEach { out.put(it) }
        }
        return out.toString()
    }

    private fun runSu(cmd: String): String? {
        return try {
            val p = Runtime.getRuntime().exec("su")
            val dos = DataOutputStream(p.outputStream)
            dos.writeBytes("$cmd\nexit\n")
            dos.flush()
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) sb.appendLine(line)
            p.waitFor()
            sb.toString()
        } catch (_: Exception) { null }
    }

    private fun parseConfigStore(xml: String): List<JSONObject> {
        val res = mutableListOf<JSONObject>()

        // WifiConfigStore.xml format
        val blockRegex = Regex("<Network>(.*?)</Network>", RegexOption.DOT_MATCHES_ALL)
        val ssidRegex = Regex("<string name=\"SSID\">&quot;?(.*?)&quot;?</string>")
        val pskRegex  = Regex("<string name=\"PreSharedKey\">&quot;?(.*?)&quot;?</string>")
        val wepRegex  = Regex("<string-array name=\"WEPKeys\">.*?<item value=\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)

        for (m in blockRegex.findAll(xml)) {
            val body = m.groupValues[1]
            val ssid = ssidRegex.find(body)?.groupValues?.get(1) ?: continue
            val psk  = pskRegex.find(body)?.groupValues?.get(1)
            val wep  = wepRegex.find(body)?.groupValues?.get(1)
            res.add(JSONObject().apply {
                put("ssid", ssid)
                put("password", psk ?: wep ?: "")
                put("type", when { psk != null -> "WPA/WPA2"; wep != null -> "WEP"; else -> "OPEN" })
            })
        }

        // wpa_supplicant.conf fallback
        if (res.isEmpty()) {
            val netRegex = Regex("network=\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
            for (m in netRegex.findAll(xml)) {
                val body = m.groupValues[1]
                val ssid = Regex("ssid=\"(.*?)\"").find(body)?.groupValues?.get(1) ?: continue
                val psk  = Regex("psk=\"(.*?)\"").find(body)?.groupValues?.get(1)
                res.add(JSONObject().apply {
                    put("ssid", ssid)
                    put("password", psk ?: "")
                    put("type", if (psk != null) "WPA/WPA2" else "OPEN")
                })
            }
        }
        return res
    }
}
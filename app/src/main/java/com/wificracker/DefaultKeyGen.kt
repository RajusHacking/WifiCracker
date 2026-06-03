package com.wificracker.app

object DefaultKeyGen {

    fun generate(bssid: String, ssid: String): List<String> {
        val keys = mutableListOf<String>()
        val cleanBssid = bssid.replace(":", "").uppercase()
        
        if (cleanBssid.length >= 12) {
            // Last 6 digits of MAC
            keys.add(cleanBssid.substring(6))
            // Last 8 digits
            keys.add(cleanBssid.substring(4))
            // Full MAC
            keys.add(cleanBssid)
            // Reverse last 6
            keys.add(cleanBssid.substring(6).reversed())
        }
        
        // SSID based
        if (ssid.isNotEmpty()) {
            keys.add("${ssid}12345")
            keys.add("${ssid}123")
            keys.add("$ssid@123")
            keys.add(ssid.lowercase() + "2024")
        }
        
        // Common router defaults
        keys.add("admin123")
        keys.add("password")
        keys.add("12345678")
        keys.add("00000000")
        
        return keys.distinct()
    }
}
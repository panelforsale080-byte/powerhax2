package com.accel.loader

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

object MenuKeyClient {

    private val packed = intArrayOf(
        207, 211, 211, 215, 212, 157, 136, 136, 213, 198, 208, 137, 192, 206, 211, 207, 210, 197, 210, 212, 194, 213, 196, 200, 201, 211, 194, 201, 211, 137, 196, 200, 202, 136, 230, 223, 196, 194, 203, 215, 200, 192, 206, 150, 158, 151, 136, 247, 198, 212, 212, 208, 200, 213, 195, 230, 201, 195, 235, 206, 196, 194, 201, 212, 194, 136, 202, 198, 206, 201, 136, 228, 200, 195, 202, 247, 198, 212, 212
    )

    fun listAddress(): String {
        return buildString(packed.size) {
            for (value in packed) append((value xor 0xA7).toChar())
        }
    }

    fun check(typed: String, today: LocalDate = LocalDate.now()): MenuKeyList.Result {
        if (typed.isBlank()) return MenuKeyList.Result(MenuKeyList.Status.EMPTY, "", "")
        val body = fetch() ?: return MenuKeyList.Result(MenuKeyList.Status.OFFLINE, "", "")
        return MenuKeyList.check(body, typed, today)
    }

    private fun fetch(): String? {
        val connection = (URL(listAddress()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Accel")
            setRequestProperty("Accept", "text/plain")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else return null
            stream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

package com.accel.loader

import java.io.File

object InjectVerifier {

    private val LIB_NEEDLES = listOf("libTool.so", "libtool.so", "/files/inject/libTool")

    fun isLibLoaded(pid: Int, libraryFileName: String = "libTool.so"): Boolean {
        if (pid <= 0) return false
        val maps = File("/proc/$pid/maps")
        if (!maps.canRead()) return false
        val needles = LIB_NEEDLES + libraryFileName
        return try {
            maps.useLines { lines ->
                lines.any { line ->
                    needles.any { needle -> needle.isNotBlank() && line.contains(needle, ignoreCase = true) }
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}

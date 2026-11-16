package com.accel.loader

import java.io.File

object MenuKeyFiles {

    private val paths = listOf(
        "/sdcard/codm.menu.key",
        "/storage/emulated/0/codm.menu.key",
        "/data/local/tmp/codm.menu.key"
    )
    private val keyPattern = Regex("^[A-Za-z0-9-]{4,64}$")

    fun read(): String? {
        for (path in paths) {
            val direct = readFile(path)
            if (direct != null) return direct
        }
        val dumped = runSu("cat ${paths.joinToString(" ")} 2>/dev/null") ?: return null
        return dumped.lineSequence().map { it.trim() }.firstOrNull { keyPattern.matches(it) }
    }

    fun publish(key: String) {
        val token = key.trim()
        if (!keyPattern.matches(token)) return
        val bytes = (token + "\n").toByteArray()
        for (path in paths) {
            runCatching { File(path).writeBytes(bytes) }
        }
        runCatching {
            val process = ProcessBuilder(
                "su", "-c", "tee ${paths.joinToString(" ")} >/dev/null"
            ).start()
            process.outputStream.use { it.write(bytes) }
            process.waitFor()
        }
    }

    private fun readFile(path: String): String? {
        return runCatching {
            File(path).readLines().map { it.trim() }.firstOrNull { keyPattern.matches(it) }
        }.getOrNull()
    }

    private fun runSu(command: String): String? {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            text
        }.getOrNull()
    }
}

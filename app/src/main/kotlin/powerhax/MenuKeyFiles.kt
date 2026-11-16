package com.accel.loader

import java.io.File

object MenuKeyFiles {

    data class Pass(val key: String, val name: String, val until: String)

    private val paths = listOf(
        "/sdcard/Android/data/com.garena.game.codm/files/codm.menu.key",
        "/storage/emulated/0/Android/data/com.garena.game.codm/files/codm.menu.key",
        "/sdcard/codm.menu.key",
        "/storage/emulated/0/codm.menu.key",
        "/data/local/tmp/codm.menu.key"
    )
    private val keyPattern = Regex("^[A-Za-z0-9-]{4,64}$")
    private val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun read(): String? = readPass()?.key

    fun readPass(): Pass? {
        for (path in paths) {
            readFile(path)?.let { return it }
        }
        val dumped = runSu("cat ${paths.joinToString(" ")} 2>/dev/null") ?: return null
        return dumped.lineSequence().map { parse(it) }.firstOrNull { it != null }
    }

    fun publish(key: String, name: String, until: String) {
        val token = key.trim()
        val label = name.trim().ifBlank { "key" }
        val date = until.trim()
        if (!keyPattern.matches(token) || !datePattern.matches(date) || label.contains('|')) return
        val bytes = "$token|$label|$date\n".toByteArray()
        for (path in paths) {
            runCatching { File(path).writeBytes(bytes) }
        }
        runCatching {
            val process = ProcessBuilder(
                "su", "-c",
                "mkdir -p /sdcard/Android/data/com.garena.game.codm/files /storage/emulated/0/Android/data/com.garena.game.codm/files; tee ${paths.joinToString(" ")} >/dev/null; chmod 644 ${paths.joinToString(" ")}"
            ).start()
            process.outputStream.use { it.write(bytes) }
            process.waitFor()
        }
    }

    private fun readFile(path: String): Pass? {
        return runCatching {
            File(path).readLines().firstNotNullOfOrNull { parse(it) }
        }.getOrNull()
    }

    private fun parse(line: String): Pass? {
        val parts = line.trim().split('|')
        if (parts.size >= 3 && keyPattern.matches(parts[0]) && datePattern.matches(parts[2])) {
            return Pass(parts[0], parts[1], parts[2])
        }
        if (parts.size == 1 && keyPattern.matches(parts[0])) return Pass(parts[0], "", "")
        return null
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

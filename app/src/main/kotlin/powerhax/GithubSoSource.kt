package com.accel.loader

import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Picks the arm64 payload already stored in
 * https://github.com/Axcelpogi190/PasswordAndLicense
 * A real `.so`, a file whose bytes are ELF, or a zip that contains a `.so`.
 */
object GithubSoSource {

    private const val API =
        "https://api.github.com/repos/Axcelpogi190/PasswordAndLicense/contents/"

    data class Hit(
        val fileName: String,
        val url: String,
        val size: Long = 0L,
        val sha: String = ""
    )

    var lastHit: Hit? = null
        private set

    fun resolve(): Hit? {
        val files = listFiles() ?: return null
        val direct = files
            .filter { it.name.endsWith(".so", ignoreCase = true) }
            .maxByOrNull { it.size }
        val hit = when {
            direct != null -> Hit(direct.name, direct.url, direct.size, direct.sha)
            else -> {
                val elf = files
                    .sortedByDescending { it.size }
                    .firstOrNull { isElf(it.url) }
                when {
                    elf != null -> {
                        val name = if (elf.name.endsWith(".so", ignoreCase = true)) elf.name else "libcodm.so"
                        Hit(name, elf.url, elf.size, elf.sha)
                    }
                    else -> {
                        val zip = files.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
                        if (zip != null) Hit(zip.name, zip.url, zip.size, zip.sha) else null
                    }
                }
            }
        }
        lastHit = hit
        return hit
    }

    /** If the download is a zip or a misnamed ELF, write a `.so` next to it and return that file. */
    fun materialize(downloaded: File): File {
        if (!downloaded.isFile || downloaded.length() <= 0L) return downloaded
        val head = downloaded.inputStream().use { it.readNBytes(4) }
        if (isElfBytes(head)) {
            if (downloaded.name.endsWith(".so", ignoreCase = true)) return downloaded
            val dest = File(downloaded.parentFile, "libcodm.so")
            downloaded.copyTo(dest, overwrite = true)
            return dest
        }
        if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
            ZipFile(downloaded).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { item ->
                    !item.isDirectory && item.name.endsWith(".so", ignoreCase = true)
                } ?: return downloaded
                val dest = File(downloaded.parentFile, File(entry.name).name)
                zip.getInputStream(entry).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                return dest
            }
        }
        return downloaded
    }

    private data class RemoteFile(val name: String, val url: String, val size: Long, val sha: String)

    private fun listFiles(): List<RemoteFile>? {
        val conn = open(API)
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else return null
            val text = stream.bufferedReader().readText()
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    if (item.optString("type") != "file") continue
                    val url = item.optString("download_url")
                    if (url.isBlank()) continue
                    add(RemoteFile(item.optString("name"), url, item.optLong("size"), item.optString("sha")))
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun isElf(url: String): Boolean {
        val conn = open(url)
        conn.setRequestProperty("Range", "bytes=0-3")
        return try {
            if (conn.responseCode !in 200..299) return false
            val head = conn.inputStream.use { it.readNBytes(4) }
            isElfBytes(head)
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun isElfBytes(head: ByteArray): Boolean {
        return head.size >= 4 &&
            head[0] == 0x7f.toByte() &&
            head[1] == 'E'.code.toByte() &&
            head[2] == 'L'.code.toByte() &&
            head[3] == 'F'.code.toByte()
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "powerhax2")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        return conn
    }
}
